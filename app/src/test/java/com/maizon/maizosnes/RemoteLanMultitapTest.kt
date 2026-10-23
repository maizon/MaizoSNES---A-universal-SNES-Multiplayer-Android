package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Validates three independent, low-latency controller streams on one host.
 * Uses actual loopback datagrams on a private IPv4 interface; no ROM/core.
 */
class RemoteLanMultitapTest {
    private fun until(timeout: Long = 4500, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
    private fun send(sock: DatagramSocket, ip: String, msg: RemoteLanWire.Message) {
        val bytes = RemoteLanWire.encode(msg)
        sock.send(DatagramPacket(bytes, bytes.size,
            InetAddress.getByName(ip), RemoteLanWire.PORT))
    }
    private fun receive(sock: DatagramSocket): RemoteLanWire.Message? {
        val packet = DatagramPacket(ByteArray(256), 256)
        return try {
            sock.receive(packet)
            RemoteLanWire.decode(packet.data, packet.offset, packet.length)
        } catch (_: java.net.SocketTimeoutException) { null }
    }

    @Test fun touchEdgesGoThroughDedicatedClientSenderAndClearOnRelease() {
        val ip = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        assumeTrue("Private IPv4 needed", ip != null)
        val view = AtomicReference(RemoteHostView())
        val clientView = AtomicReference<RemoteClientView?>(null)
        val held = ConcurrentHashMap<Int, Int>()
        val host = RemoteLanHost("Super Bomberman", "Host",
            "0123456789abcdef", onChanged = { view.set(it) },
            onButtons = { slot, mask -> held[slot] = mask })
        val client = RemoteLanClient(ip!!, "Pad via touch", "Super Bomberman",
            onChanged = { clientView.set(it) })
        try {
            assertTrue("Client should request approval", until(5000) {
                view.get().pending.isNotEmpty()
            })
            val p = view.get().pending.first()
            host.decide(p.nonce, true)
            assertTrue("Remote client must receive ACK", until(5000) {
                clientView.get()?.connected == true
            })
            client.sendButtons(1 shl 9)
            assertTrue("Touch button press should reach native host input", until(3000) {
                held[2] == (1 shl 9)
            })
            client.sendButtons(0)
            assertTrue("Touch button release must not stick", until(3000) {
                held[2] == 0
            })
        } finally {
            client.close()
            host.close()
        }
    }

    @Test fun threePadsJoinInSlotsP2P3P4AndDisconnectIndependently() {
        val local = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        assumeTrue("Private IPv4 interface needed for LAN test", local != null)
        val view = AtomicReference(RemoteHostView())
        val held = ConcurrentHashMap<Int, Int>()
        val sockets = (0..2).map { DatagramSocket().apply { soTimeout = 300 } }
        var host: RemoteLanHost? = null
        try {
            host = RemoteLanHost("Super Bomberman", "Maizon",
                "0123456789abcdef", onChanged = { view.set(it) },
                onButtons = { slot, mask -> held[slot] = mask })
            val nonces = listOf(41L, 42L, 43L)
            sockets.forEachIndexed { i, socket ->
                send(socket, local!!, RemoteLanWire.Message(
                    RemoteLanWire.HELLO, nonce = nonces[i], text = "Pad-$i"))
            }
            assertTrue("3 requests should reserve distinct slots",
                until { view.get().pending.size == 3 })
            val pendings = view.get().pending
            assertEquals(setOf(2, 3, 4), pendings.map { it.slot }.toSet())
            for (entry in pendings) host.decide(entry.nonce, true)
            assertTrue("All 3 remote pads should be admitted",
                until { view.get().peers.size == 3 })
            sockets.forEachIndexed { i, sock ->
                var accepted: RemoteLanWire.Message? = null
                assertTrue("Expected acceptance for pad $i", until {
                    accepted = receive(sock)?.takeIf { it.type == RemoteLanWire.ACCEPT }
                    accepted != null
                })
                assertEquals(i + 2, accepted!!.slot)
                send(sock, local!!, RemoteLanWire.Message(RemoteLanWire.INPUT,
                    token = accepted!!.token, sequence = 1,
                    buttons = 1 shl (i + 4)))
            }
            assertTrue("P2/P3/P4 independent button states", until {
                held[2] == (1 shl 4) && held[3] == (1 shl 5) &&
                    held[4] == (1 shl 6)
            })
            host.move(2, 4)
            assertTrue(until { held[4] == (1 shl 4) && held[2] == (1 shl 6) })
            host.kick(3)
            assertTrue(until { held[3] == 0 && view.get().peers.size == 2 })
        } finally {
            sockets.forEach { it.close() }
            host?.close()
        }
    }
}
