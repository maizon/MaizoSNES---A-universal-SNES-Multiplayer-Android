package com.maizon.maizosnes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * An opt-in, ROM-free controller-only LAN transport. Entirely separate from
 * MZNP/GekkoNet; never sends frames, states, game files or avatar images.
 *
 * This revision deliberately advertises one remote P2. The shipped native
 * Snes9x frontend only exposes P1/P2; advertising P3/P4 without native
 * multitap support would silently drop buttons.
 */
internal object RemoteLanWire {
    const val PORT = 55437
    private const val MAGIC = 0x4d5a5243 // MZRC
    private const val VERSION = 1
    const val HELLO = 1
    const val WAIT = 2
    const val ACCEPT = 3
    const val REJECT = 4
    const val INPUT = 5
    const val PING = 6
    const val PONG = 7
    const val BYE = 8
    const val MAX_BYTES = 256

    internal data class Message(
        val type: Int, val nonce: Long = 0, val token: Long = 0,
        val sequence: Int = 0, val buttons: Int = 0, val stamp: Long = 0,
        val slot: Int = 0, val text: String = ""
    )

    fun encode(m: Message): ByteArray {
        val bytes = ByteArrayOutputStream(96)
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeByte(VERSION)
            out.writeByte(m.type)
            when (m.type) {
                HELLO -> { out.writeLong(m.nonce); out.writeUTF(PlayerIdentity.sanitize(m.text).take(32)) }
                WAIT, REJECT -> { out.writeLong(m.nonce); if (m.type == REJECT) out.writeUTF(m.text.take(80)) }
                ACCEPT -> {
                    out.writeLong(m.nonce); out.writeLong(m.token)
                    out.writeByte(m.slot); out.writeUTF(m.text.take(76))
                }
                INPUT -> { out.writeLong(m.token); out.writeInt(m.sequence); out.writeShort(m.buttons and 0xffff) }
                PING, PONG -> { out.writeLong(m.token); out.writeLong(m.stamp) }
                BYE -> out.writeLong(m.token)
                else -> error("Tipo de pacote inválido")
            }
        }
        require(bytes.size() <= MAX_BYTES)
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): Message? =
        runCatching {
            if (size !in 6..MAX_BYTES || offset < 0 || offset + size > bytes.size) return null
            DataInputStream(ByteArrayInputStream(bytes, offset, size)).use { input ->
                if (input.readInt() != MAGIC || input.readUnsignedByte() != VERSION) return null
                val type = input.readUnsignedByte()
                val message = when (type) {
                    HELLO -> Message(type, nonce = input.readLong(),
                        text = PlayerIdentity.sanitize(input.readUTF()).take(32))
                    WAIT -> Message(type, nonce = input.readLong())
                    REJECT -> Message(type, nonce = input.readLong(), text = input.readUTF().take(80))
                    ACCEPT -> Message(type, nonce = input.readLong(), token = input.readLong(),
                        slot = input.readUnsignedByte(), text = input.readUTF().take(76))
                    INPUT -> Message(type, token = input.readLong(), sequence = input.readInt(),
                        buttons = input.readUnsignedShort())
                    PING, PONG -> Message(type, token = input.readLong(), stamp = input.readLong())
                    BYE -> Message(type, token = input.readLong())
                    else -> return null
                }
                if (input.available() != 0) null else message
            }
        }.getOrNull()
}

internal data class RemotePadPeer(
    val name: String, val address: String, val slot: Int = 2, val connected: Boolean = false
)
internal data class RemoteHostView(
    val enabled: Boolean = false,
    val peer: RemotePadPeer? = null,
    val pending: RemotePadPeer? = null,
    val secondsToAutoAccept: Int = 0
)
internal data class RemoteClientView(
    val title: String, val host: String, val name: String,
    val connected: Boolean = false, val status: String = "Conectando…",
    val slot: Int = 2, val rttMs: Long? = null
)

/** Host never touches Compose from the worker thread; callback marshals UI. */
internal class RemoteLanHost(
    private val title: String,
    private val owner: String,
    private val romHash: String,
    private val onChanged: (RemoteHostView) -> Unit,
    private val onButtons: (Int) -> Unit
) : Closeable {
    private data class Pending(val endpoint: InetSocketAddress, val nonce: Long,
        val name: String, val since: Long)
    private data class Peer(var endpoint: InetSocketAddress, val nonce: Long,
        val name: String, val token: Long, var lastPacket: Long,
        var lastSeq: Int = -1, var buttons: Int = 0)

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val socket = DatagramSocket(RemoteLanWire.PORT).apply { soTimeout = 90 }
    private var announcement: LanDiscovery.Announcement? = null
    private var waiting: Pending? = null
    private var peer: Peer? = null
    private val random = SecureRandom()
    @Volatile var currentButtons: Int = 0
        private set

    init {
        announcement = LanDiscovery.announce(RemoteLanWire.PORT, title, romHash,
            ownerName = owner, netcode = "Remote")
        Thread({ loop() }, "Maizo-RemoteHost-UDP").apply { isDaemon = true; start() }
        changed()
    }

    private fun changed() {
        val state = synchronized(lock) {
            val p = peer
            val w = waiting
            RemoteHostView(true, p?.let {
                RemotePadPeer(it.name, it.endpoint.address.hostAddress ?: "LAN", connected = true)
            }, w?.let {
                RemotePadPeer(it.name, it.endpoint.address.hostAddress ?: "LAN")
            }, w?.let { ((10_000 - (System.currentTimeMillis() - it.since)) / 1_000)
                .toInt().coerceIn(0, 10) } ?: 0)
        }
        onChanged(state)
    }

    private fun send(to: InetSocketAddress, msg: RemoteLanWire.Message) {
        if (closed.get()) return
        runCatching {
            val bytes = RemoteLanWire.encode(msg)
            socket.send(DatagramPacket(bytes, bytes.size, to))
        }
    }

    private fun approveInternal(p: Pending) {
        if (peer != null || waiting !== p) return
        val token = random.nextLong()
        peer = Peer(p.endpoint, p.nonce, p.name, token, System.currentTimeMillis())
        waiting = null
        send(p.endpoint, RemoteLanWire.Message(RemoteLanWire.ACCEPT,
            nonce = p.nonce, token = token, slot = 2, text = title))
    }

    fun decide(accept: Boolean) {
        synchronized(lock) {
            val p = waiting ?: return
            if (accept) approveInternal(p) else {
                waiting = null
                send(p.endpoint, RemoteLanWire.Message(RemoteLanWire.REJECT,
                    nonce = p.nonce, text = "Anfitrião recusou a conexão."))
            }
        }
        changed()
    }

    fun kick() {
        synchronized(lock) {
            val p = peer ?: return
            send(p.endpoint, RemoteLanWire.Message(RemoteLanWire.REJECT,
                nonce = p.nonce, text = "Controle desconectado pelo anfitrião."))
            peer = null
            currentButtons = 0
            onButtons(0)
        }
        changed()
    }

    private fun process(msg: RemoteLanWire.Message, address: InetSocketAddress) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (msg.type == RemoteLanWire.HELLO) {
                val existing = peer
                if (existing != null) {
                    if (existing.nonce == msg.nonce && existing.endpoint.address == address.address) {
                        existing.endpoint = address // preserve session on client socket recreation
                        existing.lastPacket = now
                        send(address, RemoteLanWire.Message(RemoteLanWire.ACCEPT,
                            nonce = existing.nonce, token = existing.token,
                            slot = 2, text = title))
                    } else send(address, RemoteLanWire.Message(RemoteLanWire.REJECT,
                        nonce = msg.nonce, text = "Jogador 2 ocupado."))
                    return
                }
                if (waiting == null) waiting = Pending(address, msg.nonce,
                    msg.text.ifBlank { "Jogador" }, now)
                val w = waiting ?: return
                if (w.endpoint == address && w.nonce == msg.nonce)
                    send(address, RemoteLanWire.Message(RemoteLanWire.WAIT, nonce = msg.nonce))
                else send(address, RemoteLanWire.Message(RemoteLanWire.REJECT,
                    nonce = msg.nonce, text = "Há outra solicitação aguardando."))
                changed()
                return
            }
            val p = peer ?: return
            if (address != p.endpoint || msg.token != p.token) return
            when (msg.type) {
                RemoteLanWire.INPUT -> {
                    // Sequence comparison handles integer wraparound and old datagrams.
                    if (p.lastSeq != -1 && msg.sequence - p.lastSeq <= 0) return
                    p.lastSeq = msg.sequence
                    p.lastPacket = now
                    currentButtons = msg.buttons and 0xffff
                    onButtons(currentButtons)
                }
                RemoteLanWire.PING -> {
                    p.lastPacket = now
                    send(address, RemoteLanWire.Message(RemoteLanWire.PONG,
                        token = p.token, stamp = msg.stamp))
                }
                RemoteLanWire.BYE -> {
                    peer = null
                    currentButtons = 0
                    onButtons(0)
                    changed()
                }
            }
        }
    }

    private fun loop() {
        val buffer = ByteArray(RemoteLanWire.MAX_BYTES)
        try {
            while (!closed.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    if (!packet.address.isSiteLocalAddress) continue
                    val message = RemoteLanWire.decode(packet.data, packet.offset, packet.length)
                        ?: continue
                    process(message, InetSocketAddress(packet.address, packet.port))
                } catch (_: SocketTimeoutException) {
                    // Timeout also drives consent and held-button watchdog.
                }
                val now = System.currentTimeMillis()
                var stateChanged = false
                synchronized(lock) {
                    val w = waiting
                    if (w != null && now - w.since >= 10_000) {
                        approveInternal(w)
                        stateChanged = true
                    } else if (w != null) stateChanged = true
                    val p = peer
                    if (p != null) {
                        if (now - p.lastPacket > 600 && currentButtons != 0) {
                            currentButtons = 0
                            onButtons(0)
                        }
                        if (now - p.lastPacket > 12_000) {
                            peer = null
                            currentButtons = 0
                            onButtons(0)
                            stateChanged = true
                        }
                    }
                }
                if (stateChanged) changed()
            }
        } catch (_: SocketException) {
            // Socket.close() is the expected shutdown signal.
        } finally {
            close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        announcement?.close()
        announcement = null
        socket.close()
        synchronized(lock) { waiting = null; peer = null; currentButtons = 0 }
        onButtons(0)
        onChanged(RemoteHostView())
    }
}

/** Controller-only client; its socket thread never starts Snes9x or fetches a ROM. */
internal class RemoteLanClient(
    private val address: String,
    private val name: String,
    private val advertisedTitle: String,
    private val onChanged: (RemoteClientView) -> Unit
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val socket = DatagramSocket().apply { soTimeout = 100 }
    private val server = InetSocketAddress(InetAddress.getByName(address), RemoteLanWire.PORT)
    private val nonce = SecureRandom().nextLong()
    private val seq = AtomicInteger()
    private val buttons = AtomicInteger()
    @Volatile private var token = 0L
    @Volatile private var connected = false
    @Volatile private var lastResponse = System.currentTimeMillis()
    @Volatile private var title = advertisedTitle
    @Volatile private var rtt: Long? = null

    init {
        Thread({ loop() }, "Maizo-RemoteClient-UDP").apply { isDaemon = true; start() }
    }

    private fun notifyState(status: String) {
        onChanged(RemoteClientView(title, address, name,
            connected = connected, status = status, rttMs = rtt))
    }

    private fun send(message: RemoteLanWire.Message) {
        if (closed.get()) return
        runCatching {
            val bytes = RemoteLanWire.encode(message)
            socket.send(DatagramPacket(bytes, bytes.size, server))
        }
    }

    fun sendButtons(mask: Int) {
        buttons.set(mask and 0xffff)
        if (connected) send(RemoteLanWire.Message(RemoteLanWire.INPUT,
            token = token, sequence = seq.getAndIncrement(), buttons = buttons.get()))
    }

    private fun loop() {
        var lastHello = 0L
        var lastInput = 0L
        var lastPing = 0L
        val buffer = ByteArray(RemoteLanWire.MAX_BYTES)
        notifyState("Aguardando aprovação do anfitrião…")
        try {
            while (!closed.get()) {
                val now = System.currentTimeMillis()
                if (connected && now - lastResponse > 4_000) {
                    connected = false
                    buttons.set(0)
                    notifyState("Reconectando à sala LAN…")
                }
                if (!connected && now - lastHello >= 500) {
                    send(RemoteLanWire.Message(RemoteLanWire.HELLO, nonce = nonce, text = name))
                    lastHello = now
                }
                if (connected && now - lastInput >= 75) {
                    sendButtons(buttons.get()) // full state heals lost release datagrams
                    lastInput = now
                }
                if (connected && now - lastPing >= 850) {
                    send(RemoteLanWire.Message(RemoteLanWire.PING,
                        token = token, stamp = System.nanoTime()))
                    lastPing = now
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    if (packet.address != server.address || packet.port != server.port) continue
                    val msg = RemoteLanWire.decode(packet.data, packet.offset, packet.length)
                        ?: continue
                    if (msg.type == RemoteLanWire.ACCEPT && msg.nonce == nonce && msg.slot == 2) {
                        token = msg.token
                        connected = true
                        title = msg.text
                        lastResponse = System.currentTimeMillis()
                        notifyState("Conectado • Jogador 2")
                        sendButtons(buttons.get())
                    } else if (msg.type == RemoteLanWire.WAIT && msg.nonce == nonce) {
                        lastResponse = System.currentTimeMillis()
                    } else if (msg.type == RemoteLanWire.PONG &&
                        connected && msg.token == token) {
                        lastResponse = System.currentTimeMillis()
                        rtt = ((System.nanoTime() - msg.stamp) / 1_000_000L).coerceAtLeast(0L)
                        notifyState("Conectado • Jogador 2")
                    } else if (msg.type == RemoteLanWire.REJECT && msg.nonce == nonce) {
                        notifyState(msg.text.ifBlank { "Conexão recusada." })
                        close()
                    }
                } catch (_: SocketTimeoutException) { }
            }
        } catch (_: SocketException) { }
        finally { socket.close() }
    }

    override fun close() {
        if (connected && !closed.get()) {
            send(RemoteLanWire.Message(RemoteLanWire.BYE, token = token))
        }
        if (!closed.compareAndSet(false, true)) return
        connected = false
        socket.close()
    }
}
