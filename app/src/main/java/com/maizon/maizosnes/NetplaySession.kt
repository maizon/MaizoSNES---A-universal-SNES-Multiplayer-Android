package com.maizon.maizosnes

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Private, experimental two-peer protocol. Host=P1, joiner=P2.
 *
 * No game bytes are sent in normal play. Sending a ROM is optional, OFF by
 * default and restricted to private-network peers after BOTH players opt in.
 * Users must have permission to redistribute the particular ROM; this feature
 * is designed for homebrew/free-redistribution content, NOT commercial games.
 *
 * Single threaded per peer: the network thread is the only source of retro_run
 * while netplay mode is enabled. Lockstep is correct-by-construction but the
 * local input-to-display delay grows with RTT; prediction/rollback is future work.
 */
internal class NetplaySession(
    private val rom: File?,
    private val coreOptions: Map<String, String>,
    private val localInput: () -> Int,
    private val prepare: (Boolean) -> Unit,
    private val saveState: () -> ByteArray?,
    private val loadState: (ByteArray) -> Int,
    private val step: (Int, Int) -> Int,
    private val stateHash: () -> Long,
    private val framePeriodNs: () -> Long,
    private val onStatus: (String) -> Unit,
    private val onFinish: (String) -> Unit,
    private val mode: NetworkMode = NetworkMode.LAN_AUTO,
    private val onMetrics: (NetworkMetrics) -> Unit = { },
    private val shareAllowedByHost: Boolean = false,
    private val receiveAllowedByClient: Boolean = false,
    private val onReceiveRom: (String, ByteArray) -> Boolean = { _, _ -> false }
) {
    companion object {
        private const val MAGIC = 0x4D5A4E50 // MZNP
        private const val VERSION = 2
        private const val MAX_ROM = 64 * 1024 * 1024
        private const val MAX_STATE = 64 * 1024 * 1024 + 28
        private const val HASH_INTERVAL = 120
        private const val TRANSFER = 1
        private const val READY = 2
        private const val DENIED = 0
        private val NO_ROM = ByteArray(32)
    }

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var announcement: LanDiscovery.Announcement? = null

    fun host(port: Int) = launch {
        require(port in 1024..65535) { "Porta deve estar entre 1024 e 65535." }
        require(rom?.isFile == true) { "O anfitrião precisa abrir uma ROM." }
        prepare(true)
        ServerSocket(port).use { listener ->
            server = listener
            listener.soTimeout = 60000
            announcement = LanDiscovery.announce(port, rom.nameWithoutExtension)
            onStatus("Sala disponível na rede local (TCP $port). Aguardando P2…")
            listener.accept().use { connection ->
                announcement?.close()
                announcement = null
                server = null
                socket = connection
                runSession(connection, host = true)
            }
        }
    }

    fun join(host: String, port: Int) = launch {
        require(host.isNotBlank() && host.length <= 253) { "Informe o IP ou domínio do anfitrião." }
        require(port in 1024..65535) { "Porta inválida." }
        require(rom?.isFile == true || receiveAllowedByClient) {
            "Abra uma ROM ou autorize receber uma ROM redistribuível."
        }
        prepare(true)
        Socket().use { connection ->
            socket = connection
            onStatus("Conectando ao anfitrião $host:$port…")
            connection.connect(InetSocketAddress(host.trim(), port), 10000)
            runSession(connection, host = false)
        }
    }

    private fun launch(action: () -> Unit) {
        check(thread == null) { "A sessão já foi iniciada." }
        thread = Thread({
            var message = "Sessão encerrada."
            try { action() } catch (error: Exception) {
                message = if (closed.get()) "Sessão encerrada pelo usuário."
                else "Conexão encerrada: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                closed.set(true)
                runCatching { announcement?.close() }
                runCatching { socket?.close() }
                runCatching { server?.close() }
                runCatching { prepare(false) }
                onFinish(message)
            }
        }, "Maizo-Netplay").apply { isDaemon = true; start() }
    }

    fun stop() {
        closed.set(true)
        runCatching { announcement?.close() }
        runCatching { socket?.close() }
        runCatching { server?.close() }
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun romDigest(file: File?): ByteArray {
        if (file == null || !file.isFile) return NO_ROM.copyOf()
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest()
    }

    private fun optionsDigest(): ByteArray {
        // Core source is pinned by scripts/fetch_snes9x.sh. No binary identity
        // guarantee across APKs; tested compatibility is still required.
        val manifest = buildString {
            append("MaizoSNES-Snes9x-921f9f7b83660eb44ad263022a57a4a029057c37-mznp2\n")
            coreOptions.toSortedMap().forEach { (key, value) ->
                append(key).append('=').append(value).append('\n')
            }
        }
        return digest(manifest.toByteArray(Charsets.UTF_8))
    }

    private fun read32(input: DataInputStream): ByteArray =
        ByteArray(32).also { input.readFully(it) }

    private fun runSession(connection: Socket, host: Boolean) {
        connection.tcpNoDelay = true
        connection.keepAlive = true
        runCatching { connection.trafficClass = 0x10 } // IPTOS_LOWDELAY best-effort
        connection.soTimeout = 20000
        val input = DataInputStream(BufferedInputStream(connection.getInputStream(), 32768))
        val output = DataOutputStream(BufferedOutputStream(connection.getOutputStream(), 32768))
        val ownRom = romDigest(rom)
        val ownOptions = optionsDigest()

        if (host) {
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.write(ownRom)
            output.write(ownOptions)
            output.writeUTF(rom!!.name.take(110))
            output.flush()

            require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                "Cliente com protocolo incompatível."
            }
            val clientRom = read32(input)
            val clientOptions = read32(input)
            val clientReceives = input.readBoolean()
            require(MessageDigest.isEqual(clientOptions, ownOptions)) {
                "Opções do núcleo diferentes. Iguale as configurações."
            }
            val matches = MessageDigest.isEqual(clientRom, ownRom)
            // Never offer commercial ROM transfer automatically, even on LAN.
            val privatePeer = connection.inetAddress is Inet4Address &&
                connection.inetAddress.isSiteLocalAddress &&
                connection.localAddress is Inet4Address &&
                connection.localAddress.isSiteLocalAddress
            val canTransfer = !matches && privatePeer &&
                shareAllowedByHost && clientReceives
            output.writeByte(if (matches) READY else if (canTransfer) TRANSFER else DENIED)
            output.flush()
            require(matches || canTransfer) {
                "ROM diferente. Transferência bloqueada: requer consentimento dos dois e rede privada."
            }
            if (canTransfer) {
                onStatus("Enviando ROM com permissão de redistribuição…")
                val bytes = rom.readBytes()
                require(bytes.isNotEmpty() && bytes.size <= MAX_ROM) { "ROM excede 64 MiB." }
                require(MessageDigest.isEqual(digest(bytes), ownRom)) {
                    "Arquivo foi alterado durante o envio."
                }
                connection.soTimeout = 90000
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
                require(input.readUnsignedByte() == 1) {
                    "O cliente recusou ou não conseguiu importar a ROM."
                }
                connection.soTimeout = 20000
            }
        } else {
            require(input.readInt() == MAGIC && input.readInt() == VERSION) {
                "Host com protocolo incompatível."
            }
            val hostRom = read32(input)
            val hostOptions = read32(input)
            val hostName = input.readUTF()
            require(hostName.length in 1..110) { "Nome de ROM inválido." }

            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.write(ownRom)
            output.write(ownOptions)
            output.writeBoolean(receiveAllowedByClient)
            output.flush()

            require(MessageDigest.isEqual(hostOptions, ownOptions)) {
                "Opções do núcleo diferentes. Iguale as configurações."
            }
            when (input.readUnsignedByte()) {
                READY -> require(MessageDigest.isEqual(hostRom, ownRom)) {
                    "Host afirma ROM correspondente, mas SHA-256 é diferente."
                }
                TRANSFER -> {
                    require(receiveAllowedByClient) { "Recebimento não autorizado." }
                    require(connection.inetAddress.isSiteLocalAddress &&
                        connection.localAddress.isSiteLocalAddress) {
                        "Transferência de ROM somente em rede privada."
                    }
                    onStatus("Recebendo ROM autorizada do host…")
                    connection.soTimeout = 90000
                    val size = input.readInt()
                    require(size in 1..MAX_ROM) { "Tamanho de ROM inválido: $size." }
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    require(MessageDigest.isEqual(digest(bytes), hostRom)) {
                        "Verificação SHA-256 da ROM transferida falhou."
                    }
                    val accepted = runCatching { onReceiveRom(hostName, bytes) }.getOrDefault(false)
                    output.writeByte(if (accepted) 1 else 0)
                    output.flush()
                    require(accepted) { "O aparelho não conseguiu instalar a ROM recebida." }
                    connection.soTimeout = 20000
                }
                DENIED -> error("ROM diferente ou ausente. Host não autorizou transferência.")
                else -> error("Resposta de negociação inválida.")
            }
        }

        onStatus("ROM e opções conferidas. Sincronizando estado inicial…")
        if (host) {
            val state = saveState() ?: error("Não foi possível criar o estado inicial.")
            require(state.size in 29..MAX_STATE) { "Estado inicial inválido." }
            output.writeInt(state.size)
            output.write(state)
            output.flush()
            require(input.readUnsignedByte() == 1) { "Cliente recusou o estado inicial." }
        } else {
            val length = input.readInt()
            require(length in 29..MAX_STATE) { "Estado remoto com tamanho inválido." }
            val state = ByteArray(length)
            input.readFully(state)
            val result = loadState(state)
            if (result != 0) {
                output.writeByte(0)
                output.flush()
                error("Erro ao restaurar estado inicial: $result.")
            }
            output.writeByte(1)
            output.flush()
        }

        connection.soTimeout = if (mode == NetworkMode.INTERNET) 12000 else 5000
        onStatus(if (host) "Conectado como jogador 1 (host)." else "Conectado como jogador 2.")
        val period = framePeriodNs().coerceIn(10000000L, 50000000L)
        var nextFrameAt = System.nanoTime()
        var frame = 0
        var measureFrames = 0
        var measureStalls = 0L
        var measureWaitNs = 0L
        var measureMaxWaitNs = 0L
        var measureStart = System.nanoTime()
        while (!closed.get()) {
            val localMask = localInput() and 0xffff
            val waitStart = System.nanoTime()
            output.writeInt(frame)
            output.writeInt(localMask)
            output.flush()
            val peerFrame = input.readInt()
            require(peerFrame == frame) { "Frame inválido: esperado $frame, veio $peerFrame." }
            val peerMask = input.readInt() and 0xffff
            val waitNs = (System.nanoTime() - waitStart).coerceAtLeast(0L)
            measureFrames++
            measureWaitNs += waitNs
            measureMaxWaitNs = maxOf(measureMaxWaitNs, waitNs)
            if (waitNs > period) measureStalls++
            val p1 = if (host) localMask else peerMask
            val p2 = if (host) peerMask else localMask
            check(step(p1, p2) == 0) { "Erro ao processar quadro $frame." }
            if (frame % HASH_INTERVAL == 0) {
                val localHash = stateHash()
                output.writeLong(localHash)
                output.flush()
                check(localHash == input.readLong()) {
                    "Dessíncronia detectada no quadro $frame; partida interrompida."
                }
            }
            frame++
            nextFrameAt += period
            val remaining = nextFrameAt - System.nanoTime()
            if (remaining > 0) LockSupport.parkNanos(remaining)
            else if (remaining < -period * 3) nextFrameAt = System.nanoTime()
            val now = System.nanoTime()
            if (now - measureStart >= 1_000_000_000L) {
                val elapsedSeconds = ((now - measureStart).toDouble() / 1e9).coerceAtLeast(0.001)
                onMetrics(NetworkMetrics(
                    frame = frame.toLong(),
                    peerWaitMs = if (measureFrames == 0) 0.0 else measureWaitNs / 1e6 / measureFrames,
                    maxPeerWaitMs = measureMaxWaitNs / 1e6,
                    effectiveFps = measureFrames / elapsedSeconds,
                    stalledFrames = measureStalls
                ))
                measureStart = now
                measureFrames = 0
                measureStalls = 0
                measureWaitNs = 0
                measureMaxWaitNs = 0
            }
        }
    }
}
