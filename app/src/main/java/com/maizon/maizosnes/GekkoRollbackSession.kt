package com.maizon.maizosnes

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Direct IPv4 rollback: the same manual TCP port negotiates initial state,
 * SHA-256 and UDP endpoints, then GekkoNet drives deterministic Snes9x frames.
 * Failure before the first gameplay frame returns to original MZNP lockstep.
 */
internal class GekkoRollbackSession(
    private val rom: File?,
    private val options: Map<String, String>,
    private val localInput: () -> Int,
    private val prepare: (Boolean) -> Unit,
    private val saveState: () -> ByteArray?,
    private val loadState: (ByteArray) -> Int,
    private val step: (Int, Int) -> Int,
    private val framePeriodNs: () -> Long,
    private val onStatus: (String) -> Unit,
    private val onMetrics: (NetworkMetrics) -> Unit,
    private val onFinish: (String) -> Unit,
    private val onFallback: (String) -> Unit,
    private val localDelay: Int = 1
) {
    private val stopped = AtomicBoolean(false)
    @Volatile private var thread: Thread? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var announcement: LanDiscovery.Announcement? = null
    fun host(port: Int) = launch(null, port)
    fun join(host: String, port: Int) = launch(host, port)
    fun stop() {
        stopped.set(true)
        runCatching { socket?.close() }
        runCatching { listener?.close() }
        runCatching { announcement?.close() }
        thread?.interrupt()
    }
    private fun hash(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        if (rom == null) return ByteArray(32)
        rom.inputStream().buffered().use { stream ->
            val buffer = ByteArray(16384)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest()
    }
    private fun launch(host: String?, port: Int) {
        check(thread == null) { "Sessão de rollback já iniciada" }
        thread = Thread({
            var fallback: String? = null
            var started = false
            var result = "Rollback encerrado."
            try {
                prepare(true)
                val snapshot = saveState()
                val clockA = System.nanoTime()
                val saved = saveState()
                val clockB = System.nanoTime()
                val restoreOk = saved != null && loadState(saved) == 0
                val clockC = System.nanoTime()
                val localPort = if (host == null) port + 1 else DatagramSocket(0).use { it.localPort }
                val udpOK = localPort in 1024..65535 && runCatching {
                    DatagramSocket(localPort).use { }
                    true
                }.getOrDefault(false)
                val benchmark = RollbackBenchmark(snapshot?.size ?: 0,
                    (clockB - clockA) / 1e6, (clockC - clockB) / 1e6,
                    udpOK && restoreOk)
                val rejection = if (rom == null) "Cliente sem ROM: transferir no Lockstep"
                    else RollbackFeasibility.rejection(benchmark)
                onStatus(if (rejection == null) "GekkoNet: testando UDP e save/load."
                    else "Rollback não viável: " + rejection + ". Preparando Lockstep.")
                val control = if (host == null) {
                    ServerSocket(port).use { server ->
                        listener = server
                        server.soTimeout = 20000
                        // Keep existing LAN discovery working when Rollback is
                        // the default (AUTO/LAN_AUTO both discover this room).
                        val fp = hash().take(8).joinToString("") { "%02x".format(it) }
                        announcement = LanDiscovery.announce(
                            port, rom?.nameWithoutExtension ?: "SNES", fp, false)
                        try {
                            onStatus("Sala LAN disponível: Gekko UDP negociado na porta " + port)
                            server.accept()
                        } finally {
                            announcement?.close()
                            announcement = null
                        }
                    }
                } else Socket().also { it.connect(InetSocketAddress(host, port), 20000) }
                socket = control
                control.use { peer ->
                    peer.soTimeout = 15000
                    val out = DataOutputStream(peer.getOutputStream())
                    val input = DataInputStream(peer.getInputStream())
                    val fingerprint = hash()
                    val settingHash = MessageDigest.getInstance("SHA-256").digest(
                        options.toSortedMap().entries.joinToString("\n") {
                            it.key + "=" + it.value
                        }.toByteArray(Charsets.UTF_8))
                    out.writeInt(0x474B5242)
                    out.writeInt(1)
                    out.writeBoolean(rejection == null)
                    out.writeInt(localPort)
                    out.writeInt(snapshot?.size ?: 0)
                    out.write(fingerprint)
                    out.write(settingHash)
                    out.flush()
                    require(input.readInt() == 0x474B5242 && input.readInt() == 1) {
                        "Outro peer não oferece GekkoNet"
                    }
                    val canPeer = input.readBoolean()
                    val peerUdpPort = input.readInt()
                    val remoteBytes = input.readInt()
                    val remoteHash = ByteArray(32).also { input.readFully(it) }
                    val remoteSettings = ByteArray(32).also { input.readFully(it) }
                    if (rejection != null || !canPeer) {
                        fallback = rejection ?: "Outro aparelho não suporta rollback"
                        return@use
                    }
                    if (!fingerprint.contentEquals(remoteHash) ||
                        !settingHash.contentEquals(remoteSettings) ||
                        remoteBytes != snapshot!!.size) {
                        fallback = "ROM, save state ou opções divergentes"
                        return@use
                    }
                    if (host == null) {
                        out.writeInt(snapshot.size)
                        out.write(snapshot)
                        out.flush()
                    } else {
                        val n = input.readInt()
                        require(n == snapshot.size && n in 1..RollbackFeasibility.MAX_STATE_BYTES) {
                            "Estado do host inválido"
                        }
                        require(loadState(ByteArray(n).also { input.readFully(it) }) == 0) {
                            "Snes9x rejeitou estado inicial do host"
                        }
                    }
                    val address = peer.inetAddress
                    require(address is Inet4Address && peerUdpPort in 1024..65535) {
                        "Rollback direto requer IPv4 e UDP"
                    }
                    out.writeBoolean(true)
                    out.flush()
                    require(input.readBoolean()) { "Peer recusou iniciar rollback" }
                    require(GekkoNative.nativeStart(localPort, address.hostAddress,
                        peerUdpPort, if (host == null) 0 else 1,
                        snapshot.size, 6, localDelay)) { "Falha ao abrir socket UDP nativo" }
                    val callbacks = object : RollbackCallbacks {
                        override fun save(): ByteArray = saveState() ?: error("Save state falhou")
                        override fun load(bytes: ByteArray): Boolean {
                            NativeBridge.nativeSetRollbackReplaying(true)
                            return loadState(bytes) == 0
                        }
                        override fun step(p1: Int, p2: Int, resimulation: Boolean): Boolean {
                            NativeBridge.nativeSetRollbackReplaying(resimulation)
                            return this@GekkoRollbackSession.step(p1, p2) == 0
                        }
                    }
                    val deadline = System.nanoTime() + 8_000_000_000L
                    var statTime = 0L
                    while (!stopped.get()) {
                        val before = System.nanoTime()
                        val advanced = GekkoNative.nativeTick(localInput(), callbacks)
                        if (advanced < 0) error("GekkoNet falhou: " + advanced)
                        val stats = GekkoNative.nativeStats()
                        if (stats.size >= 6 && stats[4] == 1 && !started) {
                            started = true
                            onStatus(if (host == null) "Conectado como host P1 (Rollback UDP)"
                                else "Conectado como cliente P2 (Rollback UDP)")
                        }
                        if (!started && System.nanoTime() > deadline) {
                            fallback = "UDP não alcança o peer: NAT ou firewall"
                            break
                        }
                        if (started && stats.size >= 6 &&
                            System.nanoTime() - statTime > 1_000_000_000L) {
                            statTime = System.nanoTime()
                            onMetrics(NetworkMetrics(stats[3].toLong(),
                                0.0, 0.0, 0.0, 0,
                                1, stats[0].toDouble(), stats[1] / 10.0,
                                stats[2].toLong(), true))
                        }
                        val waiting = framePeriodNs() - (System.nanoTime() - before)
                        if (waiting > 0) LockSupport.parkNanos(waiting)
                    }
                }
            } catch (e: Exception) {
                if (!stopped.get()) {
                    if (started) result = "Erro no GekkoNet: " + e.message
                    else fallback = "Erro durante negociação UDP: " + e.message
                }
            } finally {
                runCatching { NativeBridge.nativeSetRollbackReplaying(false) }
                runCatching { GekkoNative.nativeStop() }
                socket = null
                listener = null
                runCatching { announcement?.close() }
                announcement = null
                if (!stopped.get() && fallback != null && !started) {
                    if (host != null) runCatching { Thread.sleep(1000) }
                    onFallback(fallback!!)
                } else {
                    prepare(false)
                    onFinish(result)
                }
            }
        }, "Maizo-GekkoNet").apply { start() }
    }
}
