package com.maizon.maizosnes

import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** One Lowend peer and one default peer still use the unmodified MZNP v7 wire handshake. */
class GekkoLowEndMixedPeerTest {
    private class StubDriver : DirectRollbackDriver {
        @Volatile var fast = false
        @Volatile var connected = false
        val attempts = AtomicInteger()
        override fun configureLimitedSaving(enabled: Boolean) { }
        override fun configureFastSnapshots(enabled: Boolean) { fast = enabled }
        override fun start(localPort: Int, remoteIp: String, remotePort: Int,
            player: Int, stateBytes: Int, prediction: Int, delay: Int): Boolean {
            require(stateBytes in 33..1000)
            require(delay in 0..8)
            require(prediction == 6)
            connected = true
            attempts.incrementAndGet()
            return true
        }
        override fun tick(localInput: Int, callbacks: RollbackCallbacks): Int = 1
        override fun stats(): IntArray =
            intArrayOf(12, 4, 0, 1, if (connected) 1 else 0, 0)
        override fun setLocalDelay(frames: Int) = frames in 0..8
        override fun setReplay(enabled: Boolean) { }
        override fun setPresentationStride(stride: Int) { }
        override fun stop() { connected = false }
    }

    @Test fun lowendHostConnectsDefaultGuestWithoutNewProtocolOrRomChanges() {
        val rom = File.createTempFile("maizo-lowend-handshake", ".sfc")
        rom.writeBytes(ByteArray(512) { it.toByte() })
        val hostReady = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val closed = CountDownLatch(2)
        val port = ServerSocket(0).use { it.localPort }
        val hostDriver = StubDriver()
        val guestDriver = StubDriver()

        fun peer(host: Boolean): NetplaySession = NetplaySession(
            rom = rom, coreOptions = emptyMap(),
            localInput = { if (host) 1 else 2 },
            prepare = { }, saveState = { ByteArray(96) { 7 } },
            loadState = { if (it.size == 96) 0 else -1 },
            step = { _, _ -> 0 }, stateHash = { 7L },
            framePeriodNs = { 16_666_667L },
            onStatus = { message ->
                if (host && message.startsWith("Sala disponível")) hostReady.countDown()
                if (message.startsWith("Conectado como") && message.contains("Rollback UDP"))
                    connected.countDown()
            },
            onFinish = { closed.countDown() },
            netcodeMode = NetcodeMode.ROLLBACK,
            lowEndMode = host,
            directDriver = if (host) hostDriver else guestDriver
        )
        val server = peer(true)
        val client = peer(false)
        try {
            server.host(port)
            assertTrue("LAN host did not open", hostReady.await(5, TimeUnit.SECONDS))
            client.join("127.0.0.1", port)
            assertTrue("Mixed local optimization peers did not handshake",
                connected.await(10, TimeUnit.SECONDS))
            assertTrue(hostDriver.fast)
            assertFalse(guestDriver.fast)
            assertEquals(1, hostDriver.attempts.get())
            assertEquals(1, guestDriver.attempts.get())
        } finally {
            server.stop()
            client.stop()
            assertTrue(closed.await(6, TimeUnit.SECONDS))
            rom.delete()
        }
    }
}
