package com.maizon.maizosnes

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.NetworkInterface
import java.net.Inet4Address
import org.junit.Assume.assumeTrue
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
                if (message.startsWith("Sala disponível")) listening.countDown()
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
            onStatus = { if (isHost && it.startsWith("Sala disponível")) listening.countDown() },
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
                synchronized(errors) { errors.any { it.contains("ROM diferente") } })
        } finally {
            server.stop()
            client.stop()
            romA.delete()
            romB.delete()
        }
    }
    @Test fun authorizedPrivateLanTransferInstallsMatchingRom() {
        val rom = File.createTempFile("maizosnes-share", ".sfc")
        val content = ByteArray(4096) { (it % 251).toByte() }
        rom.writeBytes(content)
        val listening = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val stopped = CountDownLatch(2)
        val installed = CountDownLatch(1)
        val hostFrames = AtomicInteger()
        val clientFrames = AtomicInteger()
        val port = availablePort()
        val privateAddress = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        assumeTrue("Private IPv4 network interface required", privateAddress != null)
        var receivedName = ""
        var receivedBytes = ByteArray(0)

        val server = NetplaySession(
            rom = rom, coreOptions = emptyMap(), localInput = { 1 },
            prepare = { }, saveState = { ByteArray(96) },
            loadState = { 0 }, step = { p1, p2 ->
                assertEquals(1, p1); assertEquals(2, p2)
                hostFrames.incrementAndGet()
                0
            }, stateHash = { 77 }, framePeriodNs = { 16666667L },
            onStatus = {
                if (it.startsWith("Sala disponível")) listening.countDown()
                if (it.startsWith("Conectado")) connected.countDown()
            }, onFinish = { stopped.countDown() }, shareAllowedByHost = true
        )
        val client = NetplaySession(
            rom = null, coreOptions = emptyMap(), localInput = { 2 },
            prepare = { }, saveState = { null },
            loadState = { if (receivedBytes.contentEquals(content)) 0 else -1 },
            step = { p1, p2 ->
                assertEquals(1, p1); assertEquals(2, p2)
                clientFrames.incrementAndGet()
                0
            }, stateHash = { 77 }, framePeriodNs = { 16666667L },
            onStatus = { if (it.startsWith("Conectado")) connected.countDown() },
            onFinish = { stopped.countDown() },
            receiveAllowedByClient = true,
            onReceiveRomOffer = { name, hash, size ->
                assertEquals(rom.name, name)
                assertEquals(content.size, size)
                assertEquals(64, hash.length)
                true
            },
            onReceiveRom = { name, bytes ->
                receivedName = name
                receivedBytes = bytes
                installed.countDown()
                true
            }
        )
        try {
            server.host(port)
            assertTrue("Host did not listen", listening.await(4, TimeUnit.SECONDS))
            client.join(privateAddress!!, port)
            assertTrue("Client did not receive permitted ROM", installed.await(8, TimeUnit.SECONDS))
            assertTrue("Players did not connect", connected.await(8, TimeUnit.SECONDS))
            assertEquals(rom.name, receivedName)
            assertTrue(receivedBytes.contentEquals(content))
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
            assertTrue("Threads did not stop", stopped.await(5, TimeUnit.SECONDS))
            rom.delete()
        }
    }


    @Test fun hostCheatsAreAppliedToBothPeersBeforeFirstFrame() {
        val rom = File.createTempFile("maizosnes-cheat-net", ".sfc")
        rom.writeBytes(ByteArray(128) { 12 })
        val listening = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val appliedHost = java.util.concurrent.CopyOnWriteArrayList<String>()
        val appliedClient = java.util.concurrent.CopyOnWriteArrayList<String>()
        val firstFrame = CountDownLatch(2)
        val port = availablePort()
        fun session(host: Boolean) = NetplaySession(
            rom = rom,
            coreOptions = emptyMap(),
            localInput = { 0 },
            prepare = { },
            saveState = { ByteArray(100) { 1 } },
            loadState = { if (it.size == 100) 0 else -1 },
            step = { _, _ ->
                firstFrame.countDown()
                0
            },
            stateHash = { 900L },
            framePeriodNs = { 16666667L },
            allowOnlineCheats = true,
            hostCheatCodes = if (host) listOf("1234-5678") else emptyList(),
            applyOnlineCheats = { codes ->
                (if (host) appliedHost else appliedClient).addAll(codes.toList())
                codes.size
            },
            onStatus = { message ->
                if (host && message.startsWith("Sala disponível")) listening.countDown()
                if (message.startsWith("Conectado")) connected.countDown()
            },
            onFinish = { finished.countDown() }
        )
        val host = session(true)
        val client = session(false)
        try {
            host.host(port)
            assertTrue(listening.await(5, TimeUnit.SECONDS))
            client.join("127.0.0.1", port)
            assertTrue("Peers should accept identical cheats", connected.await(8, TimeUnit.SECONDS))
            assertTrue(firstFrame.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("1234-5678"), appliedHost.toList())
            assertEquals(listOf("1234-5678"), appliedClient.toList())
        } finally {
            host.stop()
            client.stop()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            rom.delete()
        }
    }

    @Test fun clientOptOutRejectsCheatMatchBeforeFirstFrame() {
        val rom = File.createTempFile("maizosnes-no-cheat", ".sfc")
        rom.writeBytes(ByteArray(256) { 11 })
        val listening = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val frames = AtomicInteger(0)
        val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
        val port = availablePort()
        fun session(host: Boolean) = NetplaySession(
            rom = rom,
            coreOptions = emptyMap(),
            localInput = { 0 },
            prepare = { },
            saveState = { ByteArray(100) },
            loadState = { 0 },
            step = { _, _ -> frames.incrementAndGet(); 0 },
            stateHash = { 0L },
            framePeriodNs = { 16666667L },
            allowOnlineCheats = host,
            hostCheatCodes = if (host) listOf("1234-5678") else emptyList(),
            applyOnlineCheats = { it.size },
            onStatus = {
                if (host && it.startsWith("Sala disponível")) listening.countDown()
            },
            onFinish = { errors.add(it); finished.countDown() }
        )
        val host = session(true)
        val client = session(false)
        try {
            host.host(port)
            assertTrue(listening.await(5, TimeUnit.SECONDS))
            client.join("127.0.0.1", port)
            assertTrue("Cheat handshake should abort", finished.await(8, TimeUnit.SECONDS))
            assertEquals("No frame may execute without consent", 0, frames.get())
            assertTrue(errors.any { it.contains("cheats", ignoreCase = true) })
        } finally {
            host.stop()
            client.stop()
            rom.delete()
        }
    }

    @Test fun mismatchedCoreSettingsAdoptHostsAllowedOptionsBeforePlaying() {
        val rom = File.createTempFile("maizosnes-options", ".sfc")
        rom.writeBytes(ByteArray(512) { (it % 42).toByte() })
        val port = availablePort()
        val hostReady = CountDownLatch(1)
        val joined = CountDownLatch(2)
        val ended = CountDownLatch(2)
        val adopted = java.util.concurrent.atomic.AtomicReference<Map<String, String>>()
        val hostValues = mapOf("snes9x_region" to "ntsc")
        val localValues = mapOf("snes9x_region" to "auto")
        fun newPeer(isHost: Boolean) = NetplaySession(
            rom = rom,
            coreOptions = if (isHost) hostValues else localValues,
            localInput = { 0 },
            prepare = { },
            saveState = { ByteArray(96) { 9 } },
            loadState = { if (it.size == 96) 0 else -1 },
            step = { _, _ -> 0 },
            stateHash = { 12 },
            framePeriodNs = { 16666667L },
            onStatus = {
                if (isHost && it.startsWith("Sala disponível")) hostReady.countDown()
                if (it.startsWith("Conectado")) joined.countDown()
            },
            onFinish = { ended.countDown() },
            applyHostCoreOptions = { options ->
                adopted.set(options)
                options == hostValues
            }
        )
        val server = newPeer(true)
        val client = newPeer(false)
        try {
            server.host(port)
            assertTrue(hostReady.await(5, TimeUnit.SECONDS))
            client.join("127.0.0.1", port)
            assertTrue("Config sync should let both peers connect", joined.await(8, TimeUnit.SECONDS))
            assertEquals(hostValues, adopted.get())
        } finally {
            server.stop()
            client.stop()
            assertTrue(ended.await(5, TimeUnit.SECONDS))
            rom.delete()
        }
    }

    @Test fun declinedRomOfferNeverDeliversBytesOrExecutesFrames() {
        val rom = File.createTempFile("maizosnes-deny-rom", ".sfc")
        rom.writeBytes(ByteArray(4096) { (it and 255).toByte() })
        val privateAddress = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        assumeTrue("Private interface required", privateAddress != null)
        val port = availablePort()
        val listening = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val offered = CountDownLatch(1)
        val deliveries = AtomicInteger()
        val frames = AtomicInteger()
        val messages = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = NetplaySession(
            rom = rom, coreOptions = emptyMap(), localInput = { 0 },
            prepare = { }, saveState = { ByteArray(96) },
            loadState = { 0 }, step = { _, _ -> frames.incrementAndGet(); 0 },
            stateHash = { 4L }, framePeriodNs = { 16666667L },
            onStatus = { if (it.startsWith("Sala disponível")) listening.countDown() },
            onFinish = { messages.add(it); finished.countDown() },
            shareAllowedByHost = true
        )
        val client = NetplaySession(
            rom = null, coreOptions = emptyMap(), localInput = { 0 },
            prepare = { }, saveState = { null }, loadState = { 0 },
            step = { _, _ -> frames.incrementAndGet(); 0 },
            stateHash = { 4L }, framePeriodNs = { 16666667L },
            onStatus = { }, onFinish = { messages.add(it); finished.countDown() },
            receiveAllowedByClient = true,
            onReceiveRomOffer = { _, _, _ -> offered.countDown(); false },
            onReceiveRom = { _, _ -> deliveries.incrementAndGet(); true }
        )
        try {
            server.host(port)
            assertTrue(listening.await(5, TimeUnit.SECONDS))
            client.join(privateAddress!!, port)
            assertTrue("ROM offer was never shown", offered.await(8, TimeUnit.SECONDS))
            assertTrue("Network workers did not stop", finished.await(8, TimeUnit.SECONDS))
            assertEquals("Denied bytes must not be imported", 0, deliveries.get())
            assertEquals("No frame may run after refusal", 0, frames.get())
            assertTrue(messages.any { it.contains("rejeit", ignoreCase = true) ||
                it.contains("recus", ignoreCase = true) })
        } finally {
            server.stop()
            client.stop()
            rom.delete()
        }
    }

}
