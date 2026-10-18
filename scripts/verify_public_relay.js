"use strict";
/** A black-box smoke test of the first-party public HTTPS directory + TCP relay.
 * Does not send ROMs, player input, or credentials.
 */
const assert = require("node:assert/strict");
const net = require("node:net");
const crypto = require("node:crypto");
const base = process.env.MAIZO_TEST_HTTP_BASE ||
  "https://maizosnes-relay-production.up.railway.app";
const expectedHost = process.env.MAIZO_TEST_TCP_HOST ||
  "tokaido.proxy.rlwy.net";
const expectedPort = Number(process.env.MAIZO_TEST_TCP_PORT || 31287);
const pause = (ms) => new Promise(resolve => setTimeout(resolve, ms));
async function rooms() {
  const response = await fetch(new URL("/api/rooms", base), { signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200, "Public directory must return HTTP 200");
  const data = await response.json();
  assert.equal(data.protocol, "maizo-relay-v1");
  assert.equal(data.relayHost, expectedHost);
  assert.equal(data.relayPort, expectedPort);
  assert.ok(Array.isArray(data.rooms));
  return data;
}
function connect(host, port) {
  return new Promise((resolve, reject) => {
    const socket = net.connect({ host, port });
    socket.setTimeout(10000, () => socket.destroy(new Error("TCP connect timeout")));
    socket.once("connect", () => { socket.setTimeout(0); resolve(socket); });
    socket.once("error", reject);
  });
}
function until(socket, expected) {
  return new Promise((resolve, reject) => {
    let buffer = "";
    const timeout = setTimeout(() => { cleanup(); reject(new Error("TCP relay READY timeout")); }, 10000);
    function cleanup() { clearTimeout(timeout); socket.off("data", onData); socket.off("error", onError); socket.off("close", onClose); }
    function onError(err) { cleanup(); reject(err); }
    function onClose() { cleanup(); reject(new Error("TCP disconnected before READY")); }
    function onData(chunk) { buffer += chunk.toString(); if (buffer.includes(expected)) { cleanup(); resolve(); } }
    socket.on("data", onData);
    socket.once("error", onError);
    socket.once("close", onClose);
  });
}
async function main() {
  const initial = await rooms();
  console.log("Directory healthy; existing rooms:", initial.rooms.length);
  const id = crypto.randomBytes(8).toString("hex");
  let host, guest;
  try {
    host = await connect(initial.relayHost, initial.relayPort);
    host.write("HOST " + id + " " + Buffer.from("MaizoSNES-smoke-test").toString("base64url") + "\n");
    let found = false;
    for (let attempt = 0; attempt < 10; attempt++) {
      if ((await rooms()).rooms.some(x => x.id === id)) { found = true; break; }
      await pause(200);
    }
    assert.ok(found, "Hosted room did not appear in public HTTPS directory");
    guest = await connect(initial.relayHost, initial.relayPort);
    const hostReady = until(host, "READY\n");
    const guestReady = until(guest, "READY\n");
    guest.write("JOIN " + id + "\n");
    await Promise.all([hostReady, guestReady]);
    console.log("SUCCESS: HTTPS registry, public TCP HOST/JOIN, and READY handshake.");
  } finally {
    host?.destroy();
    guest?.destroy();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
