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


test("HTTPS-domain WebSocket pair publishes a selectable room and streams bytes both ways", async () => {
  const crypto = require("node:crypto");
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  const active = [];
  function openWebSocket() {
    return new Promise((resolve, reject) => {
      const socket = net.connect(ports.httpPort, "127.0.0.1");
      active.push(socket);
      const key = crypto.randomBytes(16).toString("base64");
      let header = Buffer.alloc(0);
      socket.once("error", reject);
      socket.on("connect", () => socket.write(
        "GET /ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n" +
        "Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\n" +
        "Sec-WebSocket-Key: " + key + "\r\n\r\n"));
      function handshake(chunk) {
        header = Buffer.concat([header, chunk]);
        const marker = header.indexOf("\r\n\r\n");
        if (marker === -1) {
          if (header.length > 8192) reject(new Error("Excessive WS response"));
          return;
        }
        socket.off("data", handshake);
        const result = header.subarray(0, marker).toString();
        assert.match(result, /^HTTP\/1\.1 101 /);
        const expected = crypto.createHash("sha1")
          .update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
          .digest("base64");
        assert.match(result, new RegExp("Sec-WebSocket-Accept: " + expected.replace(/[+]/g, "\\+")));
        assert.equal(header.length, marker + 4);
        resolve(socket);
      }
      socket.on("data", handshake);
    });
  }
  function send(sock, payload) {
    payload = Buffer.from(payload);
    assert.ok(payload.length < 126);
    const key = crypto.randomBytes(4);
    const body = Buffer.from(payload);
    for (let i = 0; i < body.length; i++) body[i] ^= key[i % 4];
    sock.write(Buffer.concat([Buffer.from([0x82, 0x80 | payload.length]), key, body]));
  }
  function receive(sock) {
    return new Promise((resolve, reject) => {
      let pending = Buffer.alloc(0);
      const timeout = setTimeout(() => { sock.off("data", listener); reject(new Error("Timed out WS")); }, 4000);
      function listener(chunk) {
        pending = Buffer.concat([pending, chunk]);
        if (pending.length < 2) return;
        assert.equal(pending[0], 0x82);
        let length = pending[1], offset = 2;
        if (length === 126) {
          if (pending.length < 4) return;
          length = pending.readUInt16BE(2); offset = 4;
        }
        if (pending.length < offset + length) return;
        clearTimeout(timeout);
        sock.off("data", listener);
        resolve(pending.subarray(offset, offset + length));
      }
      sock.on("data", listener);
    });
  }
  try {
    const host = await openWebSocket();
    const title = Buffer.from("Homebrew").toString("base64url");
    send(host, "HOST abcdef000099 " + title + " 0123456789abcdef 0\n");
    await new Promise(resolve => setTimeout(resolve, 60));
    const list = await (await fetch("http://127.0.0.1:" +
      ports.httpPort + "/api/rooms")).json();
    assert.equal(list.websocketRelay, true);
    assert.equal(list.rooms.length, 1);
    assert.equal(list.rooms[0].websocketRelay, true);
    const guest = await openWebSocket();
    const hostReady = receive(host), guestReady = receive(guest);
    send(guest, "JOIN abcdef000099\n");
    assert.equal((await hostReady).toString(), "READY\n");
    assert.equal((await guestReady).toString(), "READY\n");
    const fromHost = receive(guest);
    send(host, Buffer.from("first binary payload"));
    assert.equal((await fromHost).toString(), "first binary payload");
    const fromGuest = receive(host);
    send(guest, Buffer.from([0, 1, 2, 254, 255]));
    assert.deepEqual(await fromGuest, Buffer.from([0, 1, 2, 254, 255]));
    const after = await (await fetch("http://127.0.0.1:" +
      ports.httpPort + "/api/rooms")).json();
    assert.equal(after.rooms.length, 0);
  } finally {
    active.forEach(s => s.destroy());
    await relay.close();
  }
});
