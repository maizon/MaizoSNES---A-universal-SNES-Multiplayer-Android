package com.maizon.maizosnes

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

/**
 * Each TCP direction is delayed by 100ms independently, modeling 200ms RTT
 * without artificially throttling packets to one packet per 100ms.
 * The old one-RTT-per-frame protocol cannot exceed about 5 FPS here.
 */
class NetplayPipeliningTest {
    private data class DelayedChunk(val atNs: Long, val bytes: ByteArray)

    private fun forwardDelayed(source: Socket, target: Socket) {
        val pending = LinkedBlockingQueue<DelayedChunk>()
        thread(name = "proxy-reader", isDaemon = true) {
            try {
                val input = source.getInputStream()
                val tmp = ByteArray(16 * 1024)
                while (!source.isClosed) {
                    val n = input.read(tmp)
                    if (n < 0) break
                    if (n != 0) pending.offer(
                        DelayedChunk(System.nanoTime() + 100_000_000L, tmp.copyOf(n)))
                }
            } catch (_: Exception) {
            } finally {
                target.close()
            }
        }
        thread(name = "proxy-writer", isDaemon = true) {
            try {
                val output = target.getOutputStream()
                while (!target.isClosed) {
                    val chunk = pending.take()
                    val ns = chunk.atNs - System.nanoTime()
                    if (ns > 0) LockSupport.parkNanos(ns)
                    output.write(chunk.bytes)
                    output.flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    @Test fun staysAboveThirtyFpsAfterHandshakeDespiteTwoHundredMsRtt() {
        val rom = File.createTempFile("mznp5-pipeline", ".sfc")
        rom.writeBytes(ByteArray(512) { it.toByte() })
        val upstreamPort = ServerSocket(0).use { it.localPort }
        val proxyPort = ServerSocket(0).use { it.localPort }
        val listening = CountDownLatch(1)
        val proxyListening = CountDownLatch(1)
        val connected = CountDownLatch(2)
        val stopped = CountDownLatch(2)
        val hostFrames = AtomicInteger(0)
        val guestFrames = AtomicInteger(0)
        val server = NetplaySession(
            rom = rom,
            coreOptions = emptyMap(),
            localInput = { 1 },
            prepare = { },
            saveState = { ByteArray(100) { 4 } },
            loadState = { 0 },
            step = { p1, p2 ->
                assertEquals(1, p1); assertEquals(2, p2)
                hostFrames.incrementAndGet(); 0
            },
            stateHash = { 1L },
            framePeriodNs = { 16_666_667L },
            onStatus = { if (it.startsWith("Sala disponível")) listening.countDown()
                         if (it.startsWith("Conectado")) connected.countDown() },
            onFinish = { stopped.countDown() },
            mode = NetworkMode.RELAY
        )
        val guest = NetplaySession(
            rom = rom,
            coreOptions = emptyMap(),
            localInput = { 2 },
            prepare = { },
            saveState = { ByteArray(100) { 4 } },
            loadState = { 0 },
            step = { p1, p2 ->
                assertEquals(1, p1); assertEquals(2, p2)
                guestFrames.incrementAndGet(); 0
            },
            stateHash = { 1L },
            framePeriodNs = { 16_666_667L },
            onStatus = { if (it.startsWith("Conectado")) connected.countDown() },
            onFinish = { stopped.countDown() },
            mode = NetworkMode.RELAY
        )
        var proxyServer: ServerSocket? = null
        var client: Socket? = null
        var upstream: Socket? = null
        val bridge = thread(start = true, isDaemon = true) {
            ServerSocket(proxyPort).use { listener ->
                proxyServer = listener
                proxyListening.countDown()
                listener.accept().use { guestSocket ->
                    client = guestSocket
                    Socket("127.0.0.1", upstreamPort).use { hostSocket ->
                        upstream = hostSocket
                        forwardDelayed(guestSocket, hostSocket)
                        forwardDelayed(hostSocket, guestSocket)
                        while (!guestSocket.isClosed && !hostSocket.isClosed) {
                            Thread.sleep(20)
                        }
                    }
                }
            }
        }
        try {
            server.host(upstreamPort)
            assertTrue("Host not listening", listening.await(4, TimeUnit.SECONDS))
            assertTrue("Proxy not listening", proxyListening.await(4, TimeUnit.SECONDS))
            guest.join("127.0.0.1", proxyPort)
            assertTrue("200ms RTT handshake failed", connected.await(10, TimeUnit.SECONDS))
            val startHost = hostFrames.get()
            val startGuest = guestFrames.get()
            Thread.sleep(2700)
            val steppedHost = hostFrames.get() - startHost
            val steppedGuest = guestFrames.get() - startGuest
            assertTrue("Host reverted to RTT-bound FPS: $steppedHost", steppedHost >= 85)
            assertTrue("Guest reverted to RTT-bound FPS: $steppedGuest", steppedGuest >= 85)
        } finally {
            server.stop()
            guest.stop()
            client?.close()
            upstream?.close()
            proxyServer?.close()
            stopped.await(6, TimeUnit.SECONDS)
            rom.delete()
        }
    }
}
