"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const net = require("node:net");
const { createRelayServer } = require("./relay");
async function connect(port) {
  return new Promise((resolve, reject) => {
    const sock = net.connect({ port, host: "127.0.0.1" }, () => resolve(sock));
    sock.once("error", reject);
  });
}
function readUntil(sock, term) {
  return new Promise((resolve, reject) => {
    let data = "";
    function onData(chunk) {
      data += chunk.toString();
      if (data.includes(term)) { sock.off("data", onData); resolve(data); }
    }
    sock.on("data", onData);
    sock.once("error", reject);
  });
}
test("room listing and bidirectional relay over outbound connections", async () => {
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  let host, guest;
  try {
    host = await connect(ports.relayPort);
    host.write("HOST abcdef012345 " + Buffer.from("Mario Kart").toString("base64url") +
      " 0123456789abcdef 1\n");
    await new Promise(resolve => setTimeout(resolve, 60));
    const directory = await fetch("http://127.0.0.1:" + ports.httpPort + "/api/rooms");
    const rooms = await directory.json();
    assert.equal(rooms.relayPort, ports.relayPort);
    assert.equal(rooms.rooms[0].game, "Mario Kart");
    assert.equal(rooms.rooms[0].romHash, "0123456789abcdef");
    assert.equal(rooms.rooms[0].cheats, true);
    guest = await connect(ports.relayPort);
    const hostReady = readUntil(host, "READY\n");
    const guestReady = readUntil(guest, "READY\n");
    guest.write("JOIN abcdef012345\n");
    assert.match(await hostReady, /READY/);
    assert.match(await guestReady, /READY/);
    const response = readUntil(guest, "hello");
    host.write("hello");
    assert.match(await response, /hello/);
    const after = await (await fetch("http://127.0.0.1:" + ports.httpPort + "/api/rooms")).json();
    assert.equal(after.rooms.length, 0);
  } finally {
    host?.destroy();
    guest?.destroy();
    await relay.close();
  }
});
