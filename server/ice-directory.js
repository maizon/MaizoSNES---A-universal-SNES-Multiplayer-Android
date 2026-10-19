"use strict";
/**
 * Optional Metered credential directory. The Railway env contains the API
 * key, never the Android APK or HTTP response. Credentials are provider-
 * generated and may be quota/expiration bound; TURN remains optional.
 *
 * MAIZO_METERED_APP: account subdomain, not arbitrary URL (prevent SSRF).
 * MAIZO_METERED_TURN_API_KEY: credential-scoped Metered REST API key.
 */
function createIceDirectory(options = {}) {
  const app = String(options.app ?? process.env.MAIZO_METERED_APP ?? "");
  const apiKey = String(options.key ?? process.env.MAIZO_METERED_TURN_API_KEY ?? "");
  const fetcher = options.fetcher ?? globalThis.fetch;
  const clock = options.clock ?? Date.now;
  const enabled = /^[a-z0-9][a-z0-9-]{0,60}[a-z0-9]$/i.test(app) &&
    apiKey.length >= 12 && apiKey.length < 300;
  let cache = null;
  let expiry = 0;
  let pending = null;
  const visitors = new Map();

  async function load() {
    if (cache !== null && clock() < expiry) return cache;
    if (pending !== null) return pending;
    pending = (async () => {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 4500);
      try {
        const url = new URL("https://" + app + ".metered.live/api/v1/turn/credentials");
        url.searchParams.set("apiKey", apiKey);
        const response = await fetcher(url.href, {
          signal: controller.signal,
          redirect: "error",
          headers: { "Accept": "application/json" }
        });
        if (!response.ok) throw new Error("Metered credentials unavailable");
        const body = await response.text();
        if (body.length > 64000) throw new Error("Oversized TURN response");
        const entries = JSON.parse(body);
        if (!Array.isArray(entries)) throw new Error("Invalid ICE response");
        const validated = entries.slice(0, 12).flatMap(item => {
          if (!item || typeof item !== "object") return [];
          const urls = (Array.isArray(item.urls) ? item.urls :
            [item.urls || item.url]).filter(x =>
              typeof x === "string" && x.length < 256 &&
              /^(?:stun|stuns|turn|turns):[a-z0-9][a-z0-9.:-]{1,240}(?:\?transport=(?:tcp|udp))?$/i.test(x));
          if (!urls.length) return [];
          const turn = urls.some(x => /^turns?:/i.test(x));
          if (turn && (typeof item.username !== "string" ||
              typeof item.credential !== "string" ||
              item.username.length > 200 || item.credential.length > 300)) return [];
          return [{
            urls, ...(turn ? { username: item.username,
              credential: item.credential } : {})
          }];
        });
        if (!validated.some(v => v.urls.some(x => /^turns?:/.test(x))))
          throw new Error("No authenticated TURN supplied by Metered");
        cache = validated;
        expiry = clock() + 5 * 60 * 1000;
        return validated;
      } finally {
        clearTimeout(timeout);
        pending = null;
      }
    })();
    return pending;
  }

  return async (req, res) => {
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Access-Control-Allow-Origin", "*");
    if (!enabled) {
      res.writeHead(200);
      res.end(JSON.stringify({ configured: false, iceServers: [] }));
      return;
    }
    const ip = req.socket.remoteAddress || "unknown";
    const current = visitors.get(ip);
    const minute = Math.floor(clock() / 60000);
    const count = current && current.minute === minute ? current.count + 1 : 1;
    visitors.set(ip, { minute, count });
    if (visitors.size > 2000) visitors.clear();
    if (count > 20) {
      res.writeHead(429);
      res.end(JSON.stringify({ error: "Rate limited" }));
      return;
    }
    try {
      const servers = await load();
      res.writeHead(200);
      res.end(JSON.stringify({ configured: true, iceServers: servers }));
    } catch (_err) {
      // Never leak API key or upstream error URLs in response/logs.
      res.writeHead(503);
      res.end(JSON.stringify({ configured: false, iceServers: [],
        error: "TURN temporarily unavailable" }));
    }
  };
}
module.exports = { createIceDirectory };
