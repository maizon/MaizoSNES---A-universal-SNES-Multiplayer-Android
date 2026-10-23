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
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

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


/** P1 is always the host. Native Snes9x port-2 multitap exposes P2/P3/P4. */
internal data class RemotePadPeer(
    val name: String,
    val address: String,
    val slot: Int,
    val connected: Boolean = false,
    val nonce: Long = 0,
    val secondsToAutoAccept: Int = 0
)
internal data class RemoteHostView(
    val enabled: Boolean = false,
    val peers: List<RemotePadPeer> = emptyList(),
    val pending: List<RemotePadPeer> = emptyList()
)
internal data class RemoteClientView(
    val title: String, val host: String, val name: String,
    val connected: Boolean = false, val status: String = "Conectando…",
    val slot: Int = 2, val rttMs: Long? = null
)

/**
 * A single UDP socket handles up to three independent low-latency controllers.
 * It advertises one *dual-mode* LAN room with the TCP game port, not another
 * exclusive controller-only room. The companion TCP listener belongs to UI.
 */
internal class RemoteLanHost(
    private val title: String,
    private val owner: String,
    private val romHash: String,
    private val onChanged: (RemoteHostView) -> Unit,
    private val onButtons: (Int, Int) -> Unit,
    private val gamePort: Int = 55435
) : Closeable {
    private data class Pending(
        var endpoint: InetSocketAddress, val nonce: Long, val name: String,
        val slot: Int, val since: Long
    )
    private data class Peer(
        var endpoint: InetSocketAddress, val nonce: Long, val name: String,
        val token: Long, var slot: Int, var lastPacket: Long,
        var sequence: Int = 0, var hasSequence: Boolean = false, var buttons: Int = 0
    )
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val socket = DatagramSocket(RemoteLanWire.PORT).apply { soTimeout = 50 }
    private val peers = linkedMapOf<Int, Peer>()
    private val waiting = linkedMapOf<Long, Pending>()
    private val rejectedUntil = linkedMapOf<Long, Long>()
    private val random = SecureRandom()
    private var announcement: LanDiscovery.Announcement? = null

    init {
        // The advertised port is the TCP game port. The client always sends
        // remote controls to the independent fixed UDP 55437.
        announcement = LanDiscovery.announce(gamePort, title, romHash,
            ownerName = owner, netcode = "Both")
        check(announcement != null) { "Descoberta LAN indisponível (UDP 55436)." }
        Thread({ loop() }, "Maizo-RemoteHost-UDP").apply { isDaemon = true; start() }
        changed()
    }

    fun currentButtons(slot: Int): Int = synchronized(lock) { peers[slot]?.buttons ?: 0 }
    fun hasPeer(slot: Int): Boolean = synchronized(lock) { slot in peers }


    private fun snapshot(): RemoteHostView {
        val now = System.currentTimeMillis()
        return RemoteHostView(
            enabled = !closed.get(),
            peers = peers.map { (slot, peer) ->
                RemotePadPeer(peer.name, peer.endpoint.address.hostAddress ?: "LAN",
                    slot, connected = true, nonce = peer.nonce)
            },
            pending = waiting.values.map { p ->
                RemotePadPeer(p.name, p.endpoint.address.hostAddress ?: "LAN", p.slot,
                    nonce = p.nonce, secondsToAutoAccept =
                        ((10_000 - (now - p.since) + 999) / 1_000).toInt().coerceIn(0, 10))
            })
    }

    private fun changed() { onChanged(synchronized(lock) { snapshot() }) }

    private fun send(to: InetSocketAddress, message: RemoteLanWire.Message) {
        if (closed.get()) return
        runCatching {
            val payload = RemoteLanWire.encode(message)
            socket.send(DatagramPacket(payload, payload.size, to))
        }
    }

    private fun accept(p: Pending) {
        if (closed.get() || waiting[p.nonce] !== p || peers.containsKey(p.slot)) return
        val token = random.nextLong()
        val entry = Peer(p.endpoint, p.nonce, p.name, token, p.slot, System.currentTimeMillis())
        peers[p.slot] = entry
        waiting.remove(p.nonce)
        send(p.endpoint, RemoteLanWire.Message(RemoteLanWire.ACCEPT,
            nonce = p.nonce, token = token, slot = p.slot, text = title))
    }

    fun decide(nonce: Long, approved: Boolean) {
        synchronized(lock) {
            val p = waiting[nonce] ?: return
            if (approved) accept(p)
            else {
                waiting.remove(nonce)
                rejectedUntil[nonce] = System.currentTimeMillis() + 30_000
                send(p.endpoint, RemoteLanWire.Message(RemoteLanWire.REJECT,
                    nonce = p.nonce, text = "O anfitrião recusou o controle."))
            }
        }
        changed()
    }

    fun kick(slot: Int) {
        synchronized(lock) {
            val p = peers.remove(slot) ?: return
            rejectedUntil[p.nonce] = System.currentTimeMillis() + 30_000
            send(p.endpoint, RemoteLanWire.Message(RemoteLanWire.REJECT,
                nonce = p.nonce, text = "Controle desconectado pelo anfitrião."))
            onButtons(slot, 0)
        }
        changed()
    }

    fun move(from: Int, to: Int) {
        if (from !in 2..4 || to !in 2..4 || from == to) return
        synchronized(lock) {
            val p = peers[from] ?: return
            val q = peers[to]
            peers.remove(from)
            peers.remove(to)
            peers[to] = p.apply { slot = to }
            if (q != null) peers[from] = q.apply { slot = from }
            onButtons(from, q?.buttons ?: 0)
            onButtons(to, p.buttons)
            for (changed in listOfNotNull(p, q)) {
                send(changed.endpoint, RemoteLanWire.Message(RemoteLanWire.ACCEPT,
                    nonce = changed.nonce, token = changed.token,
                    slot = changed.slot, text = title))
            }
        }
        changed()
    }

    private fun process(message: RemoteLanWire.Message, endpoint: InetSocketAddress) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (message.type == RemoteLanWire.HELLO) {
                if ((rejectedUntil[message.nonce] ?: 0L) > now) {
                    send(endpoint, RemoteLanWire.Message(RemoteLanWire.REJECT,
                        nonce = message.nonce, text = "Conexão rejeitada pelo anfitrião."))
                    return
                }
                // Retry/roaming with the same nonce, never grant a second slot.
                val existing = peers.values.firstOrNull {
                    it.nonce == message.nonce && it.endpoint.address == endpoint.address
                }
                if (existing != null) {
                    existing.endpoint = endpoint
                    existing.lastPacket = now
                    send(endpoint, RemoteLanWire.Message(RemoteLanWire.ACCEPT,
                        nonce = existing.nonce, token = existing.token,
                        slot = existing.slot, text = title))
                    return
                }
                var p = waiting[message.nonce]
                if (p != null && p.endpoint.address != endpoint.address) return
                if (p == null) {
                    val reserved = peers.keys + waiting.values.map { it.slot }
                    val slot = (2..4).firstOrNull { it !in reserved }
                    if (slot == null) {
                        send(endpoint, RemoteLanWire.Message(RemoteLanWire.REJECT,
                            nonce = message.nonce, text = "Todos os controles P2–P4 estão ocupados."))
                        return
                    }
                    p = Pending(endpoint, message.nonce, message.text.ifBlank { "Jogador" },
                        slot, now)
                    waiting[message.nonce] = p
                    changed()
                }
                p.endpoint = endpoint
                send(endpoint, RemoteLanWire.Message(RemoteLanWire.WAIT,
                    nonce = message.nonce))
                return
            }
            if (message.type == RemoteLanWire.BYE) {
                // A controller may cancel while the host's 10s consent is
                // pending. Do not autoaccept a client that already left.
                val pending = waiting[message.token]
                if (pending != null && pending.endpoint == endpoint) {
                    waiting.remove(message.token)
                    changed()
                    return
                }
            }
            val peer = peers.values.firstOrNull {
                it.endpoint == endpoint && it.token == message.token
            } ?: return
            when (message.type) {
                RemoteLanWire.INPUT -> {
                    if (peer.hasSequence && message.sequence - peer.sequence <= 0) return
                    peer.sequence = message.sequence
                    peer.hasSequence = true
                    peer.lastPacket = now
                    val buttons = message.buttons and 0xffff
                    if (buttons != peer.buttons) {
                        peer.buttons = buttons
                        onButtons(peer.slot, buttons) // native atomic update, no UI queue
                    }
                }
                RemoteLanWire.PING -> {
                    peer.lastPacket = now
                    send(endpoint, RemoteLanWire.Message(RemoteLanWire.PONG,
                        token = peer.token, stamp = message.stamp))
                }
                RemoteLanWire.BYE -> {
                    peers.remove(peer.slot)
                    onButtons(peer.slot, 0)
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
                    if (packet.address.isSiteLocalAddress) {
                        RemoteLanWire.decode(packet.data, packet.offset, packet.length)?.let { msg ->
                            process(msg, InetSocketAddress(packet.address, packet.port))
                        }
                    }
                } catch (_: SocketTimeoutException) { }
                var pendingChanged = false
                val now = System.currentTimeMillis()
                synchronized(lock) {
                    rejectedUntil.entries.removeAll { it.value <= now }
                    for (entry in waiting.values.toList()) {
                        if (now - entry.since >= 10_000) {
                            accept(entry)
                            pendingChanged = true
                        }
                    }
                    for (peer in peers.values.toList()) {
                        if (now - peer.lastPacket >= 650 && peer.buttons != 0) {
                            peer.buttons = 0
                            onButtons(peer.slot, 0) // no stuck buttons after radio loss
                        }
                        if (now - peer.lastPacket > 12_000) {
                            peers.remove(peer.slot)
                            onButtons(peer.slot, 0)
                            pendingChanged = true
                        }
                    }
                }
                if (pendingChanged || synchronized(lock) { waiting.isNotEmpty() }) changed()
            }
        } catch (_: SocketException) {
            // close() is the expected shutdown.
        } finally { close() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        announcement?.close()
        announcement = null
        socket.close()
        synchronized(lock) {
            for (p in peers.values) onButtons(p.slot, 0)
            peers.clear()
            waiting.clear()
            rejectedUntil.clear()
        }
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
    // Touch/gamepad events are enqueued on the UI thread, but never perform
    // socket.send() there (Android StrictMode + unpredictable UI blocking).
    // The TX worker wakes immediately on a new edge; no fixed-frame delay.
    private val sendQueue = LinkedBlockingDeque<Int>(48)
    private val exitSignal = -1
    @Volatile private var token = 0L
    @Volatile private var slot = 2
    @Volatile private var connected = false
    @Volatile private var lastResponse = System.currentTimeMillis()
    @Volatile private var title = advertisedTitle
    @Volatile private var rtt: Long? = null

    init {
        Thread({ sendLoop() }, "Maizo-RemoteClient-TX").apply { isDaemon = true; start() }
        Thread({ loop() }, "Maizo-RemoteClient-RX").apply { isDaemon = true; start() }
    }

    private fun notifyState(status: String) {
        onChanged(RemoteClientView(title, address, name,
            connected = connected, status = status, slot = slot, rttMs = rtt))
    }

    private fun send(message: RemoteLanWire.Message) {
        if (closed.get()) return
        runCatching {
            val bytes = RemoteLanWire.encode(message)
            socket.send(DatagramPacket(bytes, bytes.size, server))
        }
    }

    fun sendButtons(mask: Int) {
        val value = mask and 0xffff
        buttons.set(value)
        if (connected && !closed.get()) {
            if (!sendQueue.offerLast(value)) {
                // Congested handset: discard the oldest stale key state,
                // retaining releases and sending the latest complete mask.
                sendQueue.pollFirst()
                sendQueue.offerLast(value)
            }
        }
    }

    private fun sendLoop() {
        try {
            while (true) {
                val next = sendQueue.pollFirst(80, TimeUnit.MILLISECONDS)
                if (next == exitSignal) {
                    // BYE uses the original UDP endpoint/token and clears held
                    // buttons promptly even if Android closes the Activity.
                    val bye = RemoteLanWire.Message(RemoteLanWire.BYE,
                        token = if (connected) token else nonce)
                    val packet = RemoteLanWire.encode(bye)
                    runCatching {
                        socket.send(DatagramPacket(packet, packet.size, server))
                    }
                    break
                }
                if (next != null && connected && !closed.get())
                    send(RemoteLanWire.Message(RemoteLanWire.INPUT,
                        token = token, sequence = seq.getAndIncrement(), buttons = next))
                if (closed.get()) break
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally { socket.close() }
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
                    if (msg.type == RemoteLanWire.ACCEPT && msg.nonce == nonce && msg.slot in 2..4) {
                        token = msg.token
                        slot = msg.slot
                        connected = true
                        title = msg.text
                        lastResponse = System.currentTimeMillis()
                        notifyState("Conectado • Jogador " + slot)
                        sendButtons(buttons.get())
                    } else if (msg.type == RemoteLanWire.WAIT && msg.nonce == nonce) {
                        lastResponse = System.currentTimeMillis()
                    } else if (msg.type == RemoteLanWire.PONG &&
                        connected && msg.token == token) {
                        lastResponse = System.currentTimeMillis()
                        rtt = ((System.nanoTime() - msg.stamp) / 1_000_000L).coerceAtLeast(0L)
                        notifyState("Conectado • Jogador " + slot)
                    } else if (msg.type == RemoteLanWire.REJECT && msg.nonce == nonce) {
                        notifyState(msg.text.ifBlank { "Conexão recusada." })
                        close()
                    }
                } catch (_: SocketTimeoutException) { }
            }
        } catch (_: SocketException) { }
        finally { close() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Do not send anything or close socket on UI thread. The sender
        // emits BYE before closing its own socket.
        sendQueue.clear()
        sendQueue.offerFirst(exitSignal)
    }
}
