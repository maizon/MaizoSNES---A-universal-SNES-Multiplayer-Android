"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createRelayServer } = require("./relay");

function inbox(ws) {
  const queued = [];
  let waiting = null;
  ws.binaryType = "arraybuffer";
  ws.addEventListener("message", event => {
    const data = Buffer.from(event.data);
    if (waiting) {
      const { resolve, timer } = waiting;
      waiting = null;
      clearTimeout(timer);
      resolve(data);
    } else queued.push(data);
  });
  return {
    next() {
      if (queued.length) return Promise.resolve(queued.shift());
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { waiting = null; reject(new Error("Timed out waiting for WSS frame")); }, 5000);
        waiting = { resolve, timer };
      });
    }
  };
}
async function connect(port) {
  const ws = new WebSocket("ws://127.0.0.1:" + port + "/ws");
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("WebSocket open timeout")), 5000);
    ws.addEventListener("open", () => { clearTimeout(timer); resolve(); }, { once: true });
    ws.addEventListener("error", () => { clearTimeout(timer); reject(new Error("WebSocket handshake failed")); }, { once: true });
  });
  return ws;
}
async function waitForRoom(port) {
  for (let i = 0; i < 30; i++) {
    const data = await (await fetch("http://127.0.0.1:" + port + "/api/rooms")).json();
    if (data.rooms.length === 1) return;
    await new Promise(resolve => setTimeout(resolve, 20));
  }
  throw new Error("HOST room not advertised");
}
function patterned(size) {
  const bytes = Buffer.alloc(size);
  for (let i = 0; i < size; i++) bytes[i] = i % 251;
  return bytes;
}
test("WSS RFC6455 16/64-bit boundary: masked 65536-byte frames relay both ways", { timeout: 15000 }, async () => {
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  let host, guest;
  try {
    host = await connect(ports.httpPort);
    const fromRelayHost = inbox(host);
    host.send(Buffer.from("HOST aabbccddeeff " + Buffer.from("Homebrew").toString("base64url") +
      " 0123456789abcdef 0\n"));
    await waitForRoom(ports.httpPort);
    guest = await connect(ports.httpPort);
    const fromRelayGuest = inbox(guest);
    guest.send(Buffer.from("JOIN aabbccddeeff\n"));
    assert.equal((await fromRelayHost.next()).toString(), "READY\n");
    assert.equal((await fromRelayGuest.next()).toString(), "READY\n");
    for (const size of [126, 65535, 65536]) {
      const bytes = patterned(size);
      host.send(bytes);
      assert.deepEqual(await fromRelayGuest.next(), bytes, "host->guest length " + size);
      guest.send(bytes);
      assert.deepEqual(await fromRelayHost.next(), bytes, "guest->host length " + size);
    }
  } finally {
    host?.close(); guest?.close();
    await relay.close();
  }
});
test("WSS rejects a masked frame exceeding the 64 KiB limit", { timeout: 15000 }, async () => {
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  let host;
  try {
    host = await connect(ports.httpPort);
    host.send(patterned(65537));
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Oversized frame was not rejected")), 5000);
      host.addEventListener("close", () => { clearTimeout(timer); resolve(); }, { once: true });
    });
  } finally {
    host?.close();
    await relay.close();
  }
});
