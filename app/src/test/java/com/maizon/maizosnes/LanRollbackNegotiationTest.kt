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

/**
 * Entire MZNP v7 ROM/capability path is exercised without loading libretro,
 * Gekko JNI or distributing game data. Mock save/load deliberately declares
 * UDP preflight not viable to prove the lossless fallback.
 */
class LanRollbackNegotiationTest {
    private fun freePort() = ServerSocket(0).use { it.localPort }

    @Test fun mixedRollbackHostAndLockstepGuestStillPlayMZNP() {
        val rom = File.createTempFile("maizo-lan-mixed-", ".sfc")
        rom.writeBytes(ByteArray(512) { (it % 251).toByte() })
        val ready = CountDownLatch(1)
        val joined = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val played = AtomicInteger()
        val port = freePort()
        fun peer(host: Boolean): NetplaySession = NetplaySession(
            rom = rom, coreOptions = emptyMap(), localInput = { if (host) 1 else 2 },
            prepare = {}, saveState = { ByteArray(96) { 9 } },
            loadState = { 0 }, step = { p1, p2 ->
                assertEquals(1, p1)
                assertEquals(2, p2)
                played.incrementAndGet(); 0
            }, stateHash = { 77L }, framePeriodNs = { 16_666_667L },
            netcodeMode = if (host) NetcodeMode.ROLLBACK else NetcodeMode.LOCKSTEP,
            onStatus = {
                if (host && it.startsWith("Sala disponível")) ready.countDown()
                if (it.startsWith("Conectado como")) joined.countDown()
            }, onFinish = { finished.countDown() }
        )
        val server = peer(true)
        val guest = peer(false)
        try {
            server.host(port)
            assertTrue("Host should advertise", ready.await(6, TimeUnit.SECONDS))
            guest.join("127.0.0.1", port)
            assertTrue("V7 opt-out must keep MZNP lockstep",
                joined.await(10, TimeUnit.SECONDS))
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (played.get() < 6 && System.nanoTime() < end) Thread.sleep(15)
            assertTrue("Both players must advance", played.get() >= 6)
        } finally {
            server.stop(); guest.stop()
            assertTrue("Sessions should close", finished.await(5, TimeUnit.SECONDS))
            rom.delete()
        }
    }

    @Test fun rollbackFirstLanStillTransfersAuthorizedRomBeforeFallback() {
        val privateIp = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        assumeTrue("Private IPv4 needed to test LAN-only ROM transfer", privateIp != null)
        val rom = File.createTempFile("maizo-lan-rollback-rom-", ".sfc")
        val bytes = ByteArray(8193) { (it % 127).toByte() }
        rom.writeBytes(bytes)
        val ready = CountDownLatch(1)
        val gotRom = CountDownLatch(1)
        val joined = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val guestLoads = AtomicInteger()
        val statuses = java.util.concurrent.CopyOnWriteArrayList<String>()
        val port = freePort()
        val host = NetplaySession(
            rom = rom, coreOptions = emptyMap(), localInput = { 1 },
            prepare = {}, saveState = { ByteArray(96) { 2 } },
            // Force UDP preflight rejection, without fake native Gekko JNI.
            loadState = { -1 }, step = { _, _ -> 0 },
            stateHash = { 7L }, framePeriodNs = { 16_666_667L },
            netcodeMode = NetcodeMode.ROLLBACK, shareAllowedByHost = true,
            onStatus = {
                statuses.add(it)
                if (it.startsWith("Sala disponível")) ready.countDown()
                if (it.startsWith("Conectado como")) joined.countDown()
            }, onFinish = { finished.countDown() }
        )
        val guest = NetplaySession(
            rom = null, coreOptions = emptyMap(), localInput = { 2 },
            prepare = {}, saveState = { ByteArray(96) { 2 } },
            // First restore is host's synchronized initial state; second
            // restore is the deliberately failing local UDP preflight.
            loadState = { if (guestLoads.incrementAndGet() == 1) 0 else -1 },
            step = { _, _ -> 0 }, stateHash = { 7L },
            framePeriodNs = { 16_666_667L },
            netcodeMode = NetcodeMode.ROLLBACK, receiveAllowedByClient = true,
            onReceiveRomOffer = { _, _, length -> length == bytes.size },
            onReceiveRom = { _, payload ->
                if (payload.contentEquals(bytes)) {
                    gotRom.countDown(); true
                } else false
            },
            onStatus = {
                statuses.add(it)
                if (it.startsWith("Conectado como")) joined.countDown()
            }, onFinish = { finished.countDown() }
        )
        try {
            host.host(port)
            assertTrue("Host should accept LAN peers", ready.await(6, TimeUnit.SECONDS))
            guest.join(privateIp!!, port)
            assertTrue("ROM must arrive before UDP negotiation",
                gotRom.await(12, TimeUnit.SECONDS))
            assertTrue("Both peers must safely choose TCP after UDP refusal",
                joined.await(12, TimeUnit.SECONDS))
            assertTrue(statuses.any { it.contains("Rollback UDP não viável") })
        } finally {
            host.stop(); guest.stop()
            assertTrue(finished.await(6, TimeUnit.SECONDS))
            rom.delete()
        }
    }
}
