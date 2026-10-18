package com.maizon.maizosnes

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal data class LanRoom(
    val host: String,
    val port: Int,
    val game: String,
    val romHash: String = "",
    val cheats: Boolean = false
)

/**
 * Local-only UDP broadcast discovery. It neither uses the internet nor requires
 * Bluetooth/GPS/location permissions. Manual IPv4/TCP connection remains usable
 * when AP/client isolation or carrier hotspot blocks broadcast packets.
 */
internal object LanDiscovery {
    private const val UDP_PORT = 55436
    private const val PROBE = "MZSCAN2"
    private const val OFFER = "MZOFFER2|"
    private const val MAX_PACKET = 512

    internal class Announcement(private val socket: DatagramSocket) : Closeable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) socket.close()
        }
    }

    fun announce(tcpPort: Int, gameName: String,
        romHash: String = "", cheats: Boolean = false): Announcement? {
        if (tcpPort !in 1024..65535) return null
        val socket = try {
            DatagramSocket(UDP_PORT).apply {
                broadcast = true
                soTimeout = 1000
            }
        } catch (_: Exception) {
            return null // Host remains reachable through manual IP/port.
        }
        val announcement = Announcement(socket)
        val display = gameName.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_").take(76)
        val safeHash = romHash.lowercase().takeIf { it.matches(Regex("[a-f0-9]{16}")) }
            ?: ""
        Thread({
            val buffer = ByteArray(MAX_PACKET)
            try {
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, packet.offset, packet.length, Charsets.US_ASCII)
                        if (message != PROBE || !packet.address.isSiteLocalAddress) continue
                        val response = "$OFFER$tcpPort|$display|$safeHash|" +
                            (if (cheats) "1" else "0")
                        val encoded = response.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(encoded, encoded.size, packet.address, packet.port))
                    } catch (_: SocketTimeoutException) {
                        // Poll once per second to allow fast graceful close.
                    }
                }
            } catch (_: SocketException) {
                // Normal on close.
            } catch (_: Exception) {
                // Discovery is best effort, manual join is still available.
            } finally {
                announcement.close()
            }
        }, "Maizo-LAN-Advertise").apply { isDaemon = true; start() }
        return announcement
    }

    internal fun parseOffer(message: String, address: String): LanRoom? {
        if (!message.startsWith(OFFER)) return null
        val fields = message.removePrefix(OFFER).split('|', limit = 4)
        if (fields.size !in 2..4) return null
        val port = fields[0].toIntOrNull() ?: return null
        if (port !in 1024..65535) return null
        val game = fields[1].take(76).trim()
        if (game.isBlank()) return null
        val hash = fields.getOrNull(2).orEmpty().lowercase()
            .takeIf { it.matches(Regex("[a-f0-9]{16}")) } ?: ""
        return LanRoom(address, port, game, hash, fields.getOrNull(3) == "1")
    }

    fun scan(timeoutMillis: Long = 1750): List<LanRoom> {
        val rooms = linkedMapOf<String, LanRoom>()
        val destinations = linkedSetOf<InetAddress>()
        destinations.add(InetAddress.getByName("255.255.255.255"))
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback) continue
                for (address in network.interfaceAddresses) {
                    if (address.address is Inet4Address) {
                        address.broadcast?.let(destinations::add)
                    }
                }
            }
        }

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 220
            val request = PROBE.toByteArray(Charsets.US_ASCII)
            val deadline = System.currentTimeMillis() + timeoutMillis.coerceIn(250, 5000)
            var nextSend = 0L
            while (System.currentTimeMillis() < deadline) {
                val now = System.currentTimeMillis()
                if (now >= nextSend) {
                    for (address in destinations) {
                        runCatching {
                            socket.send(DatagramPacket(request, request.size, address, UDP_PORT))
                        }
                    }
                    nextSend = now + 650
                }
                val response = DatagramPacket(ByteArray(MAX_PACKET), MAX_PACKET)
                try {
                    socket.receive(response)
                    if (!response.address.isSiteLocalAddress) continue
                    val message = String(response.data, response.offset, response.length, Charsets.UTF_8)
                    val room = parseOffer(message, response.address.hostAddress ?: continue)
                        ?: continue
                    rooms["${room.host}:${room.port}"] = room
                } catch (_: SocketTimeoutException) {
                    // Listen until deadline.
                }
            }
        }
        return rooms.values.sortedWith(compareBy({ it.game.lowercase() }, { it.host }))
    }
}
