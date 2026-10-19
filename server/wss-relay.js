"use strict";
/**
 * RFC6455 binary-frame relay on the same HTTPS domain as /api/rooms.
 * Railway terminates HTTPS/WSS at its edge; this module never stores ROMs.
 * Peers speak the existing HOST/JOIN line inside their first WS binary frame;
 * subsequent frames carry the unchanged MZNP6 byte stream.
 * No external dependency required (Node 22).
 */
const crypto = require("node:crypto");

function attachWssRelay(directory, rooms, maxRooms = 100) {
  const sockets = new Set();
  const ID = /^[a-f0-9]{12,24}$/;
  const maxFrame = 65536;
  const maxBuffered = 2 * 1024 * 1024;
  const frame = (opcode, payload) => {
    const n = payload.length;
    const hdr = n < 126 ? Buffer.from([0x80 | opcode, n]) :
      Buffer.from([0x80 | opcode, 126, n >>> 8, n & 255]);
    return Buffer.concat([hdr, payload]);
  };
  function reject(socket, code) {
    socket.end("HTTP/1.1 " + code + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n");
  }
  directory.on("upgrade", (req, socket, head) => {
    const key = req.headers["sec-websocket-key"];
    if (req.url !== "/ws" || req.method !== "GET" ||
        String(req.headers.upgrade).toLowerCase() !== "websocket" ||
        !String(req.headers.connection || "").toLowerCase().split(/[, ]+/).includes("upgrade") ||
        req.headers["sec-websocket-version"] !== "13" ||
        typeof key !== "string" || !/^[a-zA-Z0-9+/]{22}==$/.test(key) ||
        Buffer.from(key, "base64").length !== 16 ||
        sockets.size >= maxRooms * 2 + 20) {
      reject(socket, "400 Bad Request");
      return;
    }
    const accept = crypto.createHash("sha1").update(
      key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").digest("base64");
    socket.write("HTTP/1.1 101 Switching Protocols\r\n" +
      "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
      "Sec-WebSocket-Accept: " + accept + "\r\n\r\n");
    sockets.add(socket);
    socket.setNoDelay(true);
    socket.setTimeout(5 * 60 * 1000, () => socket.destroy());
    const peer = { socket, partner: null, roomId: null, started: false, pending: Buffer.alloc(0) };
    const send = (other, payload, opcode = 2) => {
      if (other.socket.destroyed) return;
      // Never allow unlimited buffering of an upload into a slow or dead peer.
      if (other.socket.writableLength > maxBuffered) {
        other.socket.destroy();
        return;
      }
      other.socket.write(frame(opcode, payload));
    };
    socket.on("close", () => {
      sockets.delete(socket);
      if (peer.roomId && rooms.get(peer.roomId)?.peer === peer)
        rooms.delete(peer.roomId);
      peer.partner?.socket.destroy();
    });
    socket.on("error", () => socket.destroy());
    function message(payload) {
      if (!peer.started) {
        if (payload.length > 512) { socket.destroy(); return; }
        const line = payload.toString("utf8").trim();
        const parts = line.split(" ");
        const id = parts[1];
        if (!ID.test(id || "")) { socket.destroy(); return; }
        if (parts[0] === "HOST") {
          if ((parts.length !== 5 && parts.length !== 3) ||
              rooms.size >= maxRooms || rooms.has(id)) {
            send(peer, Buffer.from("ERR FULL_OR_DUPLICATE\n"));
            socket.destroySoon?.();
            return;
          }
          let title;
          try { title = Buffer.from(parts[2], "base64url").toString("utf8")
            .replace(/[\r\n\x00-\x1f]/g, "").slice(0, 75); }
          catch (_) { title = "SNES"; }
          const hash = parts.length === 5 ? parts[3] : "";
          const cheats = parts.length === 5 && parts[4] === "1";
          if ((hash && !/^[a-f0-9]{16}$/.test(hash)) ||
              (parts.length === 5 && !["0", "1"].includes(parts[4]))) {
            socket.destroy(); return;
          }
          peer.started = true;
          peer.roomId = id;
          rooms.set(id, { id, game: title || "SNES", romHash: hash,
            cheats, createdAt: Date.now(), peer });
          return;
        }
        if (parts[0] === "JOIN" && parts.length === 2) {
          const room = rooms.get(id);
          if (!room || room.peer.socket.destroyed) {
            send(peer, Buffer.from("ERR NO_ROOM\n")); socket.end(); return;
          }
          rooms.delete(id);
          room.peer.roomId = null;
          peer.started = true;
          peer.partner = room.peer;
          room.peer.partner = peer;
          socket.setTimeout(0);
          room.peer.socket.setTimeout(0);
          send(peer, Buffer.from("READY\n"));
          send(room.peer, Buffer.from("READY\n"));
          return;
        }
        socket.destroy(); return;
      }
      if (!peer.partner) { socket.destroy(); return; }
      send(peer.partner, payload);
    }
    function consume(chunk) {
      peer.pending = Buffer.concat([peer.pending, chunk]);
      if (peer.pending.length > maxBuffered) { socket.destroy(); return; }
      while (peer.pending.length >= 2) {
        const one = peer.pending[0], two = peer.pending[1];
        // Client-to-server must be masked; no fragmented or text messages.
        if ((one & 0x70) || !(one & 0x80) || !(two & 0x80)) {
          socket.destroy(); return;
        }
        let offset = 2, n = two & 127;
        if (n === 127) { socket.destroy(); return; }
        if (n === 126) {
          if (peer.pending.length < 4) return;
          n = peer.pending.readUInt16BE(2); offset = 4;
        }
        if (n > maxFrame) { socket.destroy(); return; }
        if (peer.pending.length < offset + 4 + n) return;
        const mask = peer.pending.subarray(offset, offset + 4);
        const data = Buffer.from(peer.pending.subarray(offset + 4, offset + 4 + n));
        peer.pending = peer.pending.subarray(offset + 4 + n);
        for (let k = 0; k < data.length; k++) data[k] ^= mask[k % 4];
        const opcode = one & 15;
        if (opcode === 8) { socket.end(); return; }
        if (opcode === 9) { send(peer, data, 10); continue; }
        if (opcode === 10) continue;
        if (opcode !== 2) { socket.destroy(); return; }
        message(data);
        if (socket.destroyed) return;
      }
    }
    socket.on("data", consume);
    if (head.length) consume(head);
  });
  return {
    close() { for (const socket of sockets) socket.destroy(); rooms.clear(); }
  };
}
module.exports = { attachWssRelay };
