"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createIceDirectory } = require("./ice-directory");
function res() {
  return { status: null, payload: null, headers: {},
    setHeader(name, value) { this.headers[name] = value; },
    writeHead(code) { this.status = code; },
    end(body) { this.payload = JSON.parse(body); } };
}
test("not configured: do not leak TURN secrets and preserve STUN fallback", async () => {
  const handler = createIceDirectory({ app: "", key: "" });
  const reply = res();
  await handler({ socket: { remoteAddress: "127.0.0.1" } }, reply);
  assert.equal(reply.status, 200);
  assert.equal(reply.payload.configured, false);
  assert.deepEqual(reply.payload.iceServers, []);
});
test("Metered response sanitization, HTTPS only and 5 minute cache", async () => {
  let calls = 0, now = 123450;
  const secret = "private-metered-key-12345";
  const handler = createIceDirectory({
    app: "maizo-test", key: secret, clock: () => now,
    fetcher: async (url, options) => {
      calls++;
      assert.equal(new URL(url).hostname, "maizo-test.metered.live");
      assert.equal(new URL(url).searchParams.get("apiKey"), secret);
      assert.equal(options.redirect, "error");
      return { ok: true, text: async () => JSON.stringify([
        { urls: "turn:openrelay.example.com:443?transport=udp",
          username: "ephemeral", credential: "short-lived" },
        { urls: "stun:stun1.l.google.com:19302" },
        { urls: "file:///etc/passwd", username: "bad", credential: "bad" }
      ]) };
    }
  });
  for (let i = 0; i < 2; i++) {
    const response = res();
    await handler({ socket: { remoteAddress: "127.0.0.2" } }, response);
    assert.equal(response.status, 200);
    assert.equal(response.payload.iceServers.length, 2);
    assert.equal(JSON.stringify(response.payload).includes(secret), false);
  }
  assert.equal(calls, 1);
  now += 300001;
  const response = res();
  await handler({ socket: { remoteAddress: "127.0.0.2" } }, response);
  assert.equal(response.status, 200);
  assert.equal(calls, 2);
});
test("Metered outage does not expose sensitive request details", async () => {
  const secret = "private-metered-key-12345";
  const handler = createIceDirectory({ app: "maizo-test", key: secret,
    fetcher: async () => { throw new Error(secret); } });
  const response = res();
  await handler({ socket: { remoteAddress: "127.0.0.3" } }, response);
  assert.equal(response.status, 503);
  assert.equal(JSON.stringify(response.payload).includes(secret), false);
});
test("per-IP rate limiting caps credential requests", async () => {
  const handler = createIceDirectory({
    app: "maizo-test", key: "private-metered-key-12345",
    fetcher: async () => ({ok:true, text:async () => JSON.stringify([
      { urls: "turn:relay.example.com:443", username: "temporary",
        credential: "short-lived" }
    ])})
  });
  let response;
  for(let i = 0; i < 21; i++) {
    response = res();
    await handler({ socket: { remoteAddress: "127.0.0.4" } }, response);
  }
  assert.equal(response.status, 429);
});
