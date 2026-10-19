"use strict";
/**
 * MaizoSNES experimental public-directory TCP relay.
 * Hosts and clients both make OUTBOUND TCP connections, so carrier-grade NAT
 * does not require port forwarding. This is a relay, not direct WebRTC P2P.
 * No ROM content is retained by this server. TLS is optional and terminates
 * at the relay: the server operator can inspect plaintext (not end-to-end).
 * Production deployments need HTTPS directory and firewall/rate limiting.
 */
const http = require("node:http");
const net = require("node:net");
const tls = require("node:tls");
const fs = require("node:fs");
const crypto = require("node:crypto");
const { attachWssRelay } = require("./wss-relay");
const { createIceDirectory } = require("./ice-directory");

function createRelayServer({
  httpPort = 8787, relayPort = 8788, bind = "0.0.0.0",
  publicTcpHost = "", publicTcpPort = null,
  tlsKey = null, tlsCert = null,
} = {}) {
  if (Boolean(tlsKey) !== Boolean(tlsCert)) {
    throw new Error("MAIZO_TLS_KEY_PATH and MAIZO_TLS_CERT_PATH must be set together");
  }
  const encrypted = Boolean(tlsKey && tlsCert);
  // Hosted TCP proxy ports can differ from the relay's internal listening port.
  // Advertise only values explicitly configured by the operator.
  const advertisedHost = String(publicTcpHost).trim();
  const advertisedPort = publicTcpPort == null ? null : Number(publicTcpPort);
  if (advertisedHost && !/^[a-zA-Z0-9][a-zA-Z0-9.-]{0,251}[a-zA-Z0-9]$/.test(advertisedHost)) {
    throw new Error("Invalid MAIZO_PUBLIC_TCP_HOST");
  }
  if (advertisedPort != null &&
      (!Number.isInteger(advertisedPort) || advertisedPort < 1 || advertisedPort > 65535)) {
    throw new Error("Invalid MAIZO_PUBLIC_TCP_PORT");
  }
  const rooms = new Map();
  const wsRooms = new Map();
  const maxRooms = 100;
  const roomLifetimeMs = 5 * 60 * 1000;
  const header = (r, code) => {
    r.writeHead(code, { "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store", "Access-Control-Allow-Origin": "*" });
  };
  const getIce = createIceDirectory();
  const directory = http.createServer((req, res) => {
    if (req.method === "GET" && req.url === "/api/ice") {
      getIce(req, res); return;
    }
    if (req.method !== "GET" || !["/api/rooms", "/health"].includes(req.url)) {
      header(res, 404); res.end(JSON.stringify({ error: "Not found" })); return;
    }
    header(res, 200);
    if (req.url === "/health") { res.end(JSON.stringify({ ok: true })); return; }
    res.end(JSON.stringify({
      protocol: "maizo-relay-v1",
      relayPort: advertisedPort || tcp.address()?.port || relayPort,
      relayTls: encrypted,
      ...(advertisedHost ? { relayHost: advertisedHost } : {}),
      websocketRelay: true,
      rooms: [
        ...[...rooms.values()].filter(r => !r.host.destroyed),
        ...[...wsRooms.values()].filter(r => !r.peer.socket.destroyed && r.game !== "GekkoSignaling")
      ].map(r => ({
        id: r.id, game: r.game, createdAt: r.createdAt,
        romHash: r.romHash, cheats: r.cheats,
        websocketRelay: Boolean(r.peer)
      }))
    }));
  });

  const handlePeer = (socket) => {
    socket.setNoDelay(true);
    socket.setTimeout(roomLifetimeMs, () => socket.destroy());
    let incoming = Buffer.alloc(0);
    let ownedId = null;
    function cleanup() {
      if (ownedId && rooms.get(ownedId)?.host === socket) rooms.delete(ownedId);
    }
    socket.on("close", cleanup);
    socket.on("error", () => {});
    function handshake(chunk) {
      incoming = Buffer.concat([incoming, chunk]);
      if (incoming.length > 512) { socket.destroy(); return; }
      const newline = incoming.indexOf(10);
      if (newline < 0) return;
      socket.off("data", handshake);
      const line = incoming.subarray(0, newline).toString("utf8").trim();
      const rest = incoming.subarray(newline + 1);
      const parts = line.split(" ");
      const id = parts[1];
      if (!/^[a-f0-9]{12,24}$/.test(id || "") || rest.length) {
        socket.end("ERR INVALID\n"); return;
      }
      if (parts[0] === "HOST") {
        if (rooms.size >= maxRooms || ![3, 5].includes(parts.length) || rooms.has(id)) {
          socket.end("ERR FULL_OR_DUPLICATE\n"); return;
        }
        let title;
        try {
          title = Buffer.from(parts[2], "base64url").toString("utf8")
            .replace(/[\r\n\x00-\x1f]/g, "").slice(0, 75);
        } catch (_) { title = "SNES"; }
        if (!title) title = "SNES";
        const romHash = parts.length === 5 ? parts[3] : "";
        const cheats = parts.length === 5 && parts[4] === "1";
        if ((romHash && !/^[a-f0-9]{16}$/.test(romHash)) ||
            (parts.length === 5 && !["0", "1"].includes(parts[4]))) {
          socket.end("ERR INVALID_META\n"); return;
        }
        ownedId = id;
        rooms.set(id, { id, game: title, romHash, cheats,
          createdAt: Date.now(), host: socket });
        socket.pause();
        return;
      }
      if (parts[0] === "JOIN" && parts.length === 2) {
        const room = rooms.get(id);
        if (!room || room.host.destroyed) { socket.end("ERR NO_ROOM\n"); return; }
        rooms.delete(id);
        room.host.removeAllListeners("timeout");
        room.host.setTimeout(0);
        socket.setTimeout(0);
        // Both clients wait for this exact line before starting the MZNP v3 bytes.
        room.host.write("READY\n");
        socket.write("READY\n");
        room.host.pipe(socket);
        socket.pipe(room.host);
        room.host.resume();
        socket.on("close", () => room.host.destroy());
        room.host.on("close", () => socket.destroy());
        return;
      }
      socket.end("ERR BAD_ROLE\n");
    }
    socket.on("data", handshake);
  };
  // WSS uses the public HTTPS directory (Railway edge TLS) instead of the
  // unencrypted TCP proxy. Legacy TCP rooms remain available without ROM upload.
  const websocket = attachWssRelay(directory, wsRooms, maxRooms);
  const tcp = encrypted
    ? tls.createServer({ key: tlsKey, cert: tlsCert, minVersion: "TLSv1.2" }, handlePeer)
    : net.createServer(handlePeer);

  return {
    directory, tcp,
    async start() {
      await new Promise((resolve, reject) => tcp.once("error", reject)
        .listen(relayPort, bind, resolve));
      await new Promise((resolve, reject) => directory.once("error", reject)
        .listen(httpPort, bind, resolve));
      return { httpPort: directory.address().port, relayPort: tcp.address().port };
    },
    async close() {
      for (const room of rooms.values()) room.host.destroy();
      websocket.close();
      await Promise.all([directory, tcp].map(server => new Promise(resolve => {
        server.close(() => resolve());
        server.closeAllConnections?.();
      })));
    },
  };
}
module.exports = { createRelayServer };
if (require.main === module) {
  const instance = createRelayServer({
    httpPort: Number(process.env.MAIZO_HTTP_PORT || 8787),
    relayPort: Number(process.env.MAIZO_TCP_PORT || 8788),
    bind: process.env.MAIZO_BIND || "0.0.0.0",
    publicTcpHost: process.env.MAIZO_PUBLIC_TCP_HOST || "",
    publicTcpPort: process.env.MAIZO_PUBLIC_TCP_PORT || null,
    tlsKey: process.env.MAIZO_TLS_KEY_PATH
      ? fs.readFileSync(process.env.MAIZO_TLS_KEY_PATH) : null,
    tlsCert: process.env.MAIZO_TLS_CERT_PATH
      ? fs.readFileSync(process.env.MAIZO_TLS_CERT_PATH) : null,
  });
  instance.start().then(ports => console.log("MaizoSNES relay ready", ports))
    .catch(error => { console.error(error); process.exitCode = 1; });
}
