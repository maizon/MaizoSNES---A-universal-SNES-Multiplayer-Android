package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Integration contract: always-open, non-pausing default LAN entrypoint
 * transfers a ROM only after both peers have opted in.
 */
class LanGameJoinListenerTest {
    @Test fun defaultGameRoomAllowsAuthorizedRomWithoutEarlyPausingHost() {
        val address = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        assumeTrue("Runner must have a private IPv4 LAN address", address != null)
        val port = ServerSocket(0).use { it.localPort }
        val rom = File.createTempFile("maizo-default-lan-", ".sfc")
        val bytes = ByteArray(12_345) { (it % 223).toByte() }
        rom.writeBytes(bytes)
        val didPause = AtomicBoolean(false)
        val installed = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val host = NetplaySession(
            rom = rom, coreOptions = emptyMap(), localInput = { 1 },
            prepare = { enabled -> if (enabled) didPause.set(true) },
            saveState = { ByteArray(96) { 4 } }, loadState = { 0 },
            step = { _, _ -> 0 }, stateHash = { 19L },
            framePeriodNs = { 16_666_667L },
            netcodeMode = NetcodeMode.LOCKSTEP,
            shareAllowedByHost = true,
            onStatus = { if (it.startsWith("Conectado como")) connected.countDown() },
            onFinish = { finished.countDown() }
        )
        val guest = NetplaySession(
            rom = null, coreOptions = emptyMap(), localInput = { 2 },
            prepare = { }, saveState = { null }, loadState = { 0 },
            step = { _, _ -> 0 }, stateHash = { 19L },
            framePeriodNs = { 16_666_667L },
            netcodeMode = NetcodeMode.LOCKSTEP, receiveAllowedByClient = true,
            onReceiveRomOffer = { _, _, length -> length == bytes.size },
            onReceiveRom = { _, payload ->
                if (payload.contentEquals(bytes)) {
                    installed.countDown()
                    true
                } else false
            },
            onStatus = { if (it.startsWith("Conectado como")) connected.countDown() },
            onFinish = { finished.countDown() }
        )
        val listener = LanGameJoinListener(port) { socket -> host.hostAccepted(socket) }
        try {
            assertFalse("Waiting for LAN selection must not pause host game", didPause.get())
            guest.join(address!!, port)
            assertTrue("Host should enter netplay after selection",
                connected.await(18, TimeUnit.SECONDS))
            assertTrue("Client should receive the authorized ROM",
                installed.await(10, TimeUnit.SECONDS))
            assertTrue(didPause.get())
        } finally {
            listener.close()
            host.stop()
            guest.stop()
            assertTrue("Both network sessions should stop", finished.await(6, TimeUnit.SECONDS))
            rom.delete()
        }
    }
}
