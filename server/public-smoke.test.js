"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createRelayServer } = require("./relay");
const { runSmoke, validateBase } = require("../scripts/verify_public_wss");

test("WSS smoke exercises actual local relay without ROM bytes", async () => {
  const relay = createRelayServer({ httpPort: 0, relayPort: 0, bind: "127.0.0.1" });
  const ports = await relay.start();
  try {
    const result = await runSmoke("http://127.0.0.1:" + ports.httpPort);
    assert.equal(result.status, "PASS");
    assert.equal(result.checks, 6);
  } finally {
    await relay.close();
  }
});

test("external smoke requires HTTPS; HTTP only permitted on loopback", () => {
  assert.throws(() => validateBase("http://public.example.org"));
  assert.throws(() => validateBase("https://public.example.org/secret"));
  assert.equal(validateBase("http://127.0.0.1:8787").hostname, "127.0.0.1");
});
