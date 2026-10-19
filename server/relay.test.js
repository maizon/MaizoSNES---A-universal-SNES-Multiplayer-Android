"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const net = require("node:net");
const tls = require("node:tls");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
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

test("directory advertises external TCP proxy separately from its HTTP hostname", async () => {
  const relay = createRelayServer({
    httpPort: 0, relayPort: 0, bind: "127.0.0.1",
    publicTcpHost: "example.proxy.rlwy.net", publicTcpPort: 15140,
  });
  const ports = await relay.start();
  try {
    const response = await fetch("http://127.0.0.1:" + ports.httpPort + "/api/rooms");
    const listing = await response.json();
    assert.equal(listing.protocol, "maizo-relay-v1");
    assert.equal(listing.relayHost, "example.proxy.rlwy.net");
    assert.equal(listing.relayPort, 15140);
  } finally {
    await relay.close();
  }
});
test("reject invalid advertised TCP destinations", () => {
  assert.throws(() => createRelayServer({ publicTcpHost: "http://not-allowed", publicTcpPort: 8788 }));
  assert.throws(() => createRelayServer({ publicTcpHost: "proxy.example.net", publicTcpPort: 65536 }));
});

// Self-signed certificate exists only during this test; app NEVER disables trust.
test("TLS relay advertises encryption and forwards both directions", async () => {
  const folder = fs.mkdtempSync(path.join(os.tmpdir(), "maizo-tls-"));
  const key = path.join(folder, "key.pem");
  const cert = path.join(folder, "cert.pem");
  let relay, host, guest;
  try {
    execFileSync("openssl", ["req", "-x509", "-newkey", "rsa:2048", "-nodes",
      "-days", "1", "-subj", "/CN=localhost",
      "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1",
      "-keyout", key, "-out", cert], { stdio: "ignore" });
    relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1",
      tlsKey: fs.readFileSync(key), tlsCert: fs.readFileSync(cert) });
    const ports = await relay.start();
    const listing = await (await fetch("http://127.0.0.1:" +
      ports.httpPort + "/api/rooms")).json();
    assert.equal(listing.relayTls, true);
    const dial = () => new Promise((resolve, reject) => {
      const socket = tls.connect({ host: "127.0.0.1", port: ports.relayPort,
        servername: "localhost", ca: fs.readFileSync(cert) },
        () => resolve(socket));
      socket.once("error", reject);
    });
    host = await dial();
    host.write("HOST aabbccddeeff " + Buffer.from("Freeware").toString("base64url") +
      " 0123456789abcdef 0\n");
    await new Promise(resolve => setTimeout(resolve, 40));
    guest = await dial();
    const hostReady = readUntil(host, "READY\n");
    const guestReady = readUntil(guest, "READY\n");
    guest.write("JOIN aabbccddeeff\n");
    assert.match(await hostReady, /READY/);
    assert.match(await guestReady, /READY/);
    const message = readUntil(guest, "protected");
    host.write("protected");
    assert.match(await message, /protected/);
  } finally {
    host?.destroy(); guest?.destroy();
    if (relay) await relay.close();
    fs.rmSync(folder, { recursive: true, force: true });
  }
});

test("TLS key and certificate are required together", () => {
  assert.throws(() => createRelayServer({ tlsKey: "key-only" }), /together/);
  assert.throws(() => createRelayServer({ tlsCert: "cert-only" }), /together/);
});
