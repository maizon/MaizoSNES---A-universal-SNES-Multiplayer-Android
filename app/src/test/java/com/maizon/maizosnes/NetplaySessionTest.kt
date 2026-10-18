package com.maizon.maizosnes

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Loopback protocol tests do not require ROMs or an Android device. */
class NetplaySessionTest {
    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }

    @Test fun peersExchangePerFrameInputsAndStop() {
        val rom = File.createTempFile("maizosnes-test", ".sfc")
        rom.writeBytes(ByteArray(512) { it.toByte() })
        val listening = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val hostFrames = AtomicInteger()
        val clientFrames = AtomicInteger()
        val port = availablePort()

        fun session(
            host: Boolean,
            stepCount: AtomicInteger
        ): NetplaySession = NetplaySession(
            rom = rom,
            coreOptions = mapOf("snes9x_region" to "auto"),
            localInput = { if (host) 1 else 2 },
            prepare = { },
            saveState = { ByteArray(96) { 7 } },
            loadState = { if (it.size == 96) 0 else -1 },
            step = { p1, p2 ->
                assertEquals(1, p1)
                assertEquals(2, p2)
                stepCount.incrementAndGet()
                0
            },
            stateHash = { 42L },
            framePeriodNs = { 16666667L },
            onStatus = { message ->
                if (message.startsWith("Sala aberta")) listening.countDown()
                if (message.startsWith("Conectado")) connected.countDown()
            },
            onFinish = { finished.countDown() }
        )
        val server = session(true, hostFrames)
        val client = session(false, clientFrames)
        try {
            server.host(port)
            assertTrue("Server failed to listen", listening.await(4, TimeUnit.SECONDS))
            client.join("127.0.0.1", port)
            assertTrue("Players did not connect", connected.await(8, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (hostFrames.get() < 3 || clientFrames.get() < 3) {
                if (System.nanoTime() > deadline) break
                Thread.sleep(10)
            }
            assertTrue("Host did not step", hostFrames.get() >= 3)
            assertTrue("Client did not step", clientFrames.get() >= 3)
        } finally {
            server.stop()
            client.stop()
            assertTrue("Session threads did not close", finished.await(5, TimeUnit.SECONDS))
            rom.delete()
        }
    }

    @Test fun mismatchingRomIsRejectedBeforeAnyFrame() {
        val romA = File.createTempFile("maizosnes-a", ".sfc")
        val romB = File.createTempFile("maizosnes-b", ".sfc")
        romA.writeBytes(byteArrayOf(1, 2, 3))
        romB.writeBytes(byteArrayOf(4, 5, 6))
        val listening = CountDownLatch(1)
        val stopped = CountDownLatch(2)
        val errors = mutableListOf<String>()
        val port = availablePort()
        fun make(rom: File, isHost: Boolean) = NetplaySession(
            rom = rom,
            coreOptions = emptyMap(),
            localInput = { 0 },
            prepare = { },
            saveState = { ByteArray(100) },
            loadState = { 0 },
            step = { _, _ -> error("Must not execute any frame with a mismatched ROM") },
            stateHash = { 123L },
            framePeriodNs = { 16666667L },
            onStatus = { if (isHost && it.startsWith("Sala aberta")) listening.countDown() },
            onFinish = { synchronized(errors) { errors.add(it) }; stopped.countDown() }
        )
        val server = make(romA, true)
        val client = make(romB, false)
        try {
            server.host(port)
            assertTrue(listening.await(4, TimeUnit.SECONDS))
            client.join("127.0.0.1", port)
            assertTrue("Expected handshake failure", stopped.await(8, TimeUnit.SECONDS))
            assertTrue("Expected ROM mismatch explanation",
                synchronized(errors) { errors.any { it.contains("ROMs diferem") } })
        } finally {
            server.stop()
            client.stop()
            romA.delete()
            romB.delete()
        }
    }
}
