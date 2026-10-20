package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Exercises both sides of the real MZNP v7 TCP handshake, with injectable
 * Gekko socket engine. No native lib, unauthorized ROM or Android emulator
 * required. Real Wi-Fi/UDP remains a separate device acceptance test.
 */
class DirectRollbackHandshakeTest {
    private fun port() = ServerSocket(0).use { it.localPort }
    private fun privateAddress(): String? =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress

    private class FakeUdp(private val failFirst: Int = 0) : DirectRollbackDriver {
        val starts = AtomicInteger()
        val ticks = AtomicInteger()
        @Volatile private var connected = false
        override fun configureLimitedSaving(enabled: Boolean) {}
        override fun start(localPort: Int, remoteIp: String, remotePort: Int,
                           player: Int, stateBytes: Int,
                           prediction: Int, delay: Int): Boolean {
            require(localPort in 1024..65535 && remotePort in 1024..65535)
            require(stateBytes > 32)
            connected = starts.incrementAndGet() > failFirst
            return connected
        }
        override fun tick(localInput: Int, callbacks: RollbackCallbacks): Int {
            ticks.incrementAndGet()
            if (localInput != 0) callbacks.step(1, 2, false)
            return 1
        }
        override fun stats(): IntArray = intArrayOf(2, 0, 0, ticks.get(), if (connected) 1 else 0, 0)
        override fun setLocalDelay(frames: Int) = true
        override fun setReplay(enabled: Boolean) {}
        override fun setPresentationStride(stride: Int) {}
        override fun stop() { connected = false }
    }

    @Test fun firstTwoPortFailuresThenThirdHandshakeRunsRollback() {
        val rom = File.createTempFile("maizo-retry", ".sfc")
        rom.writeBytes(ByteArray(2048) { (it % 97).toByte() })
        val a = FakeUdp(failFirst = 2)
        val b = FakeUdp(failFirst = 2)
        val ready = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val statuses = java.util.concurrent.CopyOnWriteArrayList<String>()
        val tcpPort = port()
        fun peer(host: Boolean, driver: FakeUdp): NetplaySession = NetplaySession(
            rom = rom, coreOptions = emptyMap(),
            localInput = { if (host) 1 else 2 }, prepare = {},
            saveState = { ByteArray(96) { 3 } }, loadState = { 0 },
            step = { p1, p2 -> assertEquals(1, p1); assertEquals(2, p2); 0 },
            stateHash = { 44L }, framePeriodNs = { 16_666_667L },
            onStatus = { msg ->
                statuses.add(msg)
                if (host && msg.startsWith("Sala disponível")) ready.countDown()
                if (msg.startsWith("Conectado como") && msg.contains("Rollback UDP"))
                    connected.countDown()
            }, onFinish = { finished.countDown() },
            netcodeMode = NetcodeMode.ROLLBACK, directDriver = driver
        )
        val server = peer(true, a)
        val client = peer(false, b)
        try {
            server.host(tcpPort)
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            client.join("127.0.0.1", tcpPort)
            assertTrue("Third ephemeral UDP negotiation should connect",
                connected.await(9, TimeUnit.SECONDS))
            assertEquals(3, a.starts.get())
            assertEquals(3, b.starts.get())
            assertTrue(statuses.any { it.contains("tentativa 3/3") })
        } finally {
            server.stop(); client.stop()
            assertTrue(finished.await(6, TimeUnit.SECONDS))
            rom.delete()
        }
    }

    @Test fun allThreeUdpFailuresFallBackOverSameTCPConnection() {
        val rom = File.createTempFile("maizo-fallback", ".sfc")
        rom.writeBytes(ByteArray(512) { it.toByte() })
        val a = FakeUdp(failFirst = 10)
        val b = FakeUdp(failFirst = 10)
        val ready = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val frames = AtomicInteger()
        val tcpPort = port()
        fun peer(host: Boolean, driver: FakeUdp): NetplaySession = NetplaySession(
            rom = rom, coreOptions = emptyMap(),
            localInput = { if (host) 1 else 2 }, prepare = {},
            saveState = { ByteArray(96) { 5 } }, loadState = { 0 },
            step = { _, _ -> frames.incrementAndGet(); 0 },
            stateHash = { 12L }, framePeriodNs = { 16_666_667L },
            onStatus = { msg ->
                if (host && msg.startsWith("Sala disponível")) ready.countDown()
                if (msg.startsWith("Conectado como") && !msg.contains("Rollback"))
                    connected.countDown()
            }, onFinish = { finished.countDown() },
            netcodeMode = NetcodeMode.ROLLBACK, directDriver = driver
        )
        val server = peer(true, a)
        val client = peer(false, b)
        try {
            server.host(tcpPort)
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            client.join("127.0.0.1", tcpPort)
            assertTrue("Both peers must resume Lockstep automatically",
                connected.await(9, TimeUnit.SECONDS))
            assertEquals(3, a.starts.get())
            assertEquals(3, b.starts.get())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (frames.get() < 6 && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("Original TCP must remain playable", frames.get() >= 6)
        } finally {
            server.stop(); client.stop()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            rom.delete()
        }
    }

    @Test fun guestWithoutRomReceivesAuthorizedBytesBeforeRealUdpNegotiation() {
        val ip = privateAddress()
        assumeTrue("Private IPv4 needed for rights-checked LAN transfer", ip != null)
        val rom = File.createTempFile("maizo-transfer", ".sfc")
        val content = ByteArray(10007) { (it % 117).toByte() }
        rom.writeBytes(content)
        val a = FakeUdp()
        val b = FakeUdp()
        val ready = CountDownLatch(1)
        val imported = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val tcpPort = port()
        val host = NetplaySession(
            rom = rom, coreOptions = emptyMap(), localInput = { 1 }, prepare = {},
            saveState = { ByteArray(96) { 5 } }, loadState = { 0 }, step = { _, _ -> 0 },
            stateHash = { 100L }, framePeriodNs = { 16_666_667L },
            onStatus = { msg ->
                if (msg.startsWith("Sala disponível")) ready.countDown()
                if (msg.contains("Conectado como") && msg.contains("Rollback")) connected.countDown()
            }, onFinish = { finished.countDown() },
            netcodeMode = NetcodeMode.ROLLBACK,
            shareAllowedByHost = true, directDriver = a
        )
        val guest = NetplaySession(
            rom = null, coreOptions = emptyMap(), localInput = { 2 }, prepare = {},
            saveState = { ByteArray(96) { 5 } }, loadState = { 0 }, step = { _, _ -> 0 },
            stateHash = { 100L }, framePeriodNs = { 16_666_667L },
            onStatus = { msg ->
                if (msg.contains("Conectado como") && msg.contains("Rollback")) connected.countDown()
            }, onFinish = { finished.countDown() },
            netcodeMode = NetcodeMode.ROLLBACK,
            receiveAllowedByClient = true,
            onReceiveRomOffer = { _, _, size -> size == content.size },
            onReceiveRom = { _, bytes ->
                if (bytes.contentEquals(content) && b.starts.get() == 0) {
                    imported.countDown(); true
                } else false
            }, directDriver = b
        )
        try {
            host.host(tcpPort)
            assertTrue(ready.await(6, TimeUnit.SECONDS))
            guest.join(ip!!, tcpPort)
            assertTrue("ROM must arrive first", imported.await(8, TimeUnit.SECONDS))
            assertTrue("Gekko rollback should follow authorized ROM transfer",
                connected.await(9, TimeUnit.SECONDS))
            assertEquals(1, a.starts.get())
            assertEquals(1, b.starts.get())
        } finally {
            host.stop(); guest.stop()
            assertTrue(finished.await(6, TimeUnit.SECONDS))
            rom.delete()
        }
    }
    @Test fun wrongLocalRomIsReplacedByAuthorizedHostRomBeforeRollback() {
        val address = privateAddress()
        assumeTrue("Private IPv4 required", address != null)
        val source = File.createTempFile("maizo-host-different-", ".sfc")
        val wrong = File.createTempFile("maizo-guest-different-", ".sfc")
        val original = ByteArray(7169) { (it % 223).toByte() }
        source.writeBytes(original)
        wrong.writeBytes(ByteArray(8192) { 11 })
        val a = FakeUdp()
        val b = FakeUdp()
        val ready = CountDownLatch(1)
        val imported = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val stopped = CountDownLatch(2)
        val tcpPort = port()
        fun peer(host: Boolean): NetplaySession = NetplaySession(
            rom = if (host) source else wrong, coreOptions = emptyMap(),
            localInput = { if (host) 1 else 2 }, prepare = {},
            saveState = { ByteArray(96) { 9 } }, loadState = { 0 },
            step = { _, _ -> 0 }, stateHash = { 41L },
            framePeriodNs = { 16_666_667L },
            onStatus = { status ->
                if (host && status.startsWith("Sala disponível")) ready.countDown()
                if (status.contains("Conectado como") &&
                    status.contains("Rollback")) connected.countDown()
            }, onFinish = { stopped.countDown() },
            netcodeMode = NetcodeMode.ROLLBACK,
            shareAllowedByHost = host,
            receiveAllowedByClient = !host,
            onReceiveRomOffer = { _, digest, bytes ->
                digest.length == 64 && bytes == original.size
            },
            onReceiveRom = { _, bytes ->
                if (bytes.contentEquals(original) && b.starts.get() == 0) {
                    imported.countDown()
                    true
                } else false
            },
            directDriver = if (host) a else b
        )
        val host = peer(true)
        val guest = peer(false)
        try {
            host.host(tcpPort)
            assertTrue(ready.await(6, TimeUnit.SECONDS))
            guest.join(address!!, tcpPort)
            assertTrue("Wrong ROM should be replaced before starting UDP",
                imported.await(9, TimeUnit.SECONDS))
            assertTrue("Both peers should connect using new authorized ROM",
                connected.await(10, TimeUnit.SECONDS))
            assertEquals(1, a.starts.get())
            assertEquals(1, b.starts.get())
        } finally {
            host.stop(); guest.stop()
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
            source.delete(); wrong.delete()
        }
    }

}
