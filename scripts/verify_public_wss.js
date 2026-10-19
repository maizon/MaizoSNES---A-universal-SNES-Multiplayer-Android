"use strict";
/** HTTPS/WSS black-box smoke: ephemeral room + synthetic bytes, never ROMs. */
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const DEFAULT_BASE = "https://maizosnes-relay-production.up.railway.app";
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function validateBase(value) {
  const base = new URL(value);
  const local = base.protocol === "http:" &&
    ["localhost", "127.0.0.1", "[::1]"].includes(base.hostname);
  assert.ok(base.protocol === "https:" || local,
    "Public relay requires HTTPS; HTTP is allowed only for loopback tests");
  assert.equal(base.username, "");
  assert.equal(base.password, "");
  assert.equal(base.search, "");
  assert.equal(base.hash, "");
  assert.ok(base.pathname === "/" || base.pathname === "",
    "Use a base URL without a path");
  return base;
}

async function directory(base) {
  const response = await fetch(new URL("/api/rooms", base), {
    signal: AbortSignal.timeout(10000), redirect: "error",
  });
  assert.equal(response.status, 200, "/api/rooms must return HTTP 200");
  const data = await response.json();
  assert.equal(data.protocol, "maizo-relay-v1");
  assert.equal(data.websocketRelay, true);
  assert.ok(Array.isArray(data.rooms));
  return data;
}

function openSocket(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    socket.binaryType = "arraybuffer";
    const timer = setTimeout(() => finish(new Error("WebSocket upgrade timeout")), 10000);
    function finish(error) {
      clearTimeout(timer);
      socket.removeEventListener("open", onOpen);
      socket.removeEventListener("error", onError);
      if (error) { try { socket.close(); } catch (_) {} reject(error); }
      else resolve(socket);
    }
    function onOpen() { finish(); }
    function onError() { finish(new Error("WSS upgrade/TLS handshake failed")); }
    socket.addEventListener("open", onOpen);
    socket.addEventListener("error", onError);
  });
}

function nextBinary(socket) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => finish(new Error("Relay response timeout")), 10000);
    function finish(error, data) {
      clearTimeout(timer);
      socket.removeEventListener("message", onMessage);
      socket.removeEventListener("error", onError);
      socket.removeEventListener("close", onClose);
      if (error) reject(error); else resolve(data);
    }
    function onMessage(event) {
      if (!(event.data instanceof ArrayBuffer)) {
        finish(new Error("Relay sent a non-binary WebSocket message")); return;
      }
      finish(null, Buffer.from(event.data));
    }
    function onError() { finish(new Error("Relay socket error")); }
    function onClose() { finish(new Error("Relay closed before response")); }
    socket.addEventListener("message", onMessage);
    socket.addEventListener("error", onError);
    socket.addEventListener("close", onClose);
  });
}

async function runSmoke(baseUrl = process.env.MAIZO_TEST_HTTP_BASE || DEFAULT_BASE) {
  assert.equal(typeof WebSocket, "function", "Node.js 22+ required");
  const base = validateBase(baseUrl);
  await directory(base);
  const ws = new URL("/ws", base);
  ws.protocol = base.protocol === "https:" ? "wss:" : "ws:";
  const roomId = crypto.randomBytes(8).toString("hex");
  let host, guest;
  try {
    host = await openSocket(ws);
    const title = Buffer.from("MaizoSNES smoke (no ROM)").toString("base64url");
    host.send(Buffer.from("HOST " + roomId + " " + title + " 0123456789abcdef 0\n"));
    let published = false;
    for (let i = 0; i < 12; i++) {
      const listing = await directory(base);
      if (listing.rooms.some(room => room.id === roomId && room.websocketRelay)) {
        published = true; break;
      }
      await delay(200);
    }
    assert.ok(published, "WSS room not advertised in /api/rooms");
    guest = await openSocket(ws);
    const hostReady = nextBinary(host);
    const guestReady = nextBinary(guest);
    guest.send(Buffer.from("JOIN " + roomId + "\n"));
    assert.equal((await hostReady).toString(), "READY\n");
    assert.equal((await guestReady).toString(), "READY\n");
    const guestData = nextBinary(guest);
    const payload = Buffer.from([0, 1, 2, 127, 128, 254, 255]);
    host.send(payload);
    assert.deepEqual(await guestData, payload);
    const hostData = nextBinary(host);
    const reply = Buffer.from("synthetic client reply");
    guest.send(reply);
    assert.deepEqual(await hostData, reply);
    assert.ok(!(await directory(base)).rooms.some(room => room.id === roomId));
    return { endpoint: base.origin, roomId, status: "PASS", checks: 6 };
  } finally {
    guest?.close();
    host?.close();
  }
}

module.exports = { runSmoke, validateBase };
if (require.main === module) {
  runSmoke().then(result => console.log("PUBLIC_WSS_SMOKE_PASS", JSON.stringify(result)))
    .catch(error => { console.error("PUBLIC_WSS_SMOKE_FAIL", error); process.exitCode = 1; });
}
