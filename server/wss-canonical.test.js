"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const net = require("node:net");
const { createRelayServer } = require("./relay");

// Raw masked client frames: WebSocket APIs always generate canonical lengths.
async function sendRawFrame(port, extendedBits, payloadSize) {
  const socket = net.createConnection({ host: "127.0.0.1", port });
  try {
    await new Promise((resolve, reject) => {
      socket.once("connect", resolve);
      socket.once("error", reject);
    });
    const key = Buffer.alloc(16, 11).toString("base64");
    socket.write("GET /ws HTTP/1.1\r\nHost: localhost\r\n" +
      "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
      "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + key + "\r\n\r\n");
    await new Promise((resolve, reject) => {
      let response = "";
      const timer = setTimeout(() => reject(new Error("WSS upgrade timed out")), 3000);
      const onData = chunk => {
        response += chunk.toString("latin1");
        if (response.includes("\r\n\r\n")) {
          clearTimeout(timer);
          socket.off("data", onData);
          try { assert.match(response, /^HTTP\/1\.1 101 /); resolve(); }
          catch (err) { reject(err); }
        }
      };
      socket.on("data", onData);
      socket.once("close", () => {
        clearTimeout(timer);
        reject(new Error("Socket closed before upgrade"));
      });
    });
    const header = Buffer.alloc(extendedBits === 16 ? 4 : 10);
    header[0] = 0x82; // FIN + binary
    header[1] = 0x80 | (extendedBits === 16 ? 126 : 127);
    if (extendedBits === 16) header.writeUInt16BE(payloadSize, 2);
    else header.writeBigUInt64BE(BigInt(payloadSize), 2);
    const mask = Buffer.from([0, 0, 0, 0]);
    const body = Buffer.alloc(payloadSize, 0x41);
    const closed = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Noncanonical frame was accepted")), 3000);
      socket.once("close", () => { clearTimeout(timer); resolve(); });
      socket.once("error", err => { clearTimeout(timer); if (err.code === "ECONNRESET") resolve(); else reject(err); });
    });
    socket.write(Buffer.concat([header, mask, body]));
    await closed;
  } finally {
    socket.destroy();
  }
}

for (const [bits, size] of [[16, 125], [64, 65535]]) {
  test(`WSS rejects noncanonical ${bits}-bit frame length ${size}`, { timeout: 10000 }, async () => {
    const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
    const ports = await relay.start();
    try { await sendRawFrame(ports.httpPort, bits, size); }
    finally { await relay.close(); }
  });
}
