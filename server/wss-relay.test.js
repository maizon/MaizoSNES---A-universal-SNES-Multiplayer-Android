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

test("Gekko signaling room stays hidden and carries SDP, trickle ICE and bilateral fallback", { timeout: 15000 }, async () => {
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  let host, guest;
  try {
    host = await connect(ports.httpPort);
    const hostInbox = inbox(host);
    const id = "faced00dcafe1234";
    host.send(Buffer.from("HOST " + id + " " +
      Buffer.from("GekkoSignaling").toString("base64url") +
      " 0000000000000000 0\n"));
    // The signaling room must never be shown as a playable game.
    await new Promise(resolve => setTimeout(resolve, 100));
    const directory = await (await fetch("http://127.0.0.1:" + ports.httpPort + "/api/rooms")).json();
    assert.deepEqual(directory.rooms, []);
    guest = await connect(ports.httpPort);
    const guestInbox = inbox(guest);
    guest.send(Buffer.from("JOIN " + id + "\n"));
    assert.equal((await hostInbox.next()).toString(), "READY\n");
    assert.equal((await guestInbox.next()).toString(), "READY\n");

    // DataOutputStream.writeBoolean: both peers must agree on TURN availability.
    host.send(Buffer.from([1]));
    guest.send(Buffer.from([1]));
    assert.deepEqual(await hostInbox.next(), Buffer.from([1]));
    assert.deepEqual(await guestInbox.next(), Buffer.from([1]));

    // DataOutputStream.writeInt + UTF-8 SDP. WssRelaySocket splits large
    // writes into <=16KiB WS frames; DataInputStream must reassemble bytes.
    const offerBody = Buffer.from("v=0\r\na=ice-ufrag:test\r\n".repeat(900));
    const offerLength = Buffer.alloc(4);
    offerLength.writeUInt32BE(offerBody.length);
    const offer = Buffer.concat([offerLength, offerBody]);
    host.send(offer.subarray(0, 16384));
    host.send(offer.subarray(16384));
    const receivedOffer = Buffer.concat([await guestInbox.next(), await guestInbox.next()]);
    assert.deepEqual(receivedOffer, offer);

    const answerBody = Buffer.from("v=0\r\na=ice-pwd:answer\r\n");
    const answerLength = Buffer.alloc(4);
    answerLength.writeUInt32BE(answerBody.length);
    const answer = Buffer.concat([answerLength, answerBody]);
    guest.send(answer);
    assert.deepEqual(await hostInbox.next(), answer);

    // Trickle candidate: tag=1, writeUTF(mid), int(mLine), writeUTF(candidate).
    const utf = value => {
      const data = Buffer.from(value, "utf8");
      const length = Buffer.alloc(2);
      length.writeUInt16BE(data.length);
      return Buffer.concat([length, data]);
    };
    const candidate = Buffer.concat([
      Buffer.from([1]), utf("0"), Buffer.from([0, 0, 0, 0]),
      utf("candidate:1 1 UDP 1 192.0.2.1 12345 typ host")
    ]);
    host.send(candidate);
    assert.deepEqual(await guestInbox.next(), candidate);

    // Tag=3 plus boolean=0 means fallback. Both sides must see the decision.
    host.send(Buffer.from([3, 0]));
    guest.send(Buffer.from([3, 0]));
    assert.deepEqual(await guestInbox.next(), Buffer.from([3, 0]));
    assert.deepEqual(await hostInbox.next(), Buffer.from([3, 0]));
    assert.deepEqual((await (await fetch("http://127.0.0.1:" + ports.httpPort + "/api/rooms")).json()).rooms, []);
  } finally {
    host?.close(); guest?.close();
    await relay.close();
  }
});

test("RFC6455 rejects oversized ping instead of sending an invalid pong", { timeout: 10000 }, async () => {
  const net = require("node:net");
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  let socket;
  try {
    socket = net.createConnection({ host: "127.0.0.1", port: ports.httpPort });
    await new Promise((resolve, reject) => {
      socket.once("connect", resolve);
      socket.once("error", reject);
    });
    const key = Buffer.alloc(16, 7).toString("base64");
    socket.write("GET /ws HTTP/1.1\r\nHost: localhost\r\n" +
      "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
      "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + key + "\r\n\r\n");
    await new Promise((resolve, reject) => {
      let response = "";
      const timer = setTimeout(() => reject(new Error("upgrade timed out")), 3000);
      const onData = chunk => {
        response += chunk.toString("latin1");
        if (response.includes("\r\n\r\n")) {
          clearTimeout(timer);
          socket.off("data", onData);
          try { assert.ok(response.startsWith("HTTP/1.1 101 ")); resolve(); }
          catch (err) { reject(err); }
        }
      };
      socket.on("data", onData);
    });
    // FIN + PING, masked extended length 126, payload 126 (illegal control).
    const mask = Buffer.from([1, 2, 3, 4]);
    const masked = Buffer.alloc(126);
    for (let i = 0; i < masked.length; i++) masked[i] = mask[i % 4];
    socket.write(Buffer.concat([Buffer.from([0x89, 0xfe, 0, 126]), mask, masked]));
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("oversized ping was accepted")), 3000);
      socket.once("close", () => { clearTimeout(timer); resolve(); });
    });
  } finally {
    socket?.destroy();
    await relay.close();
  }
});
