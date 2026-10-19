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
 * while netplay mode is enabled. MZNP v5 sends frame inputs in advance using
 * a bounded jitter buffer. Unlike v4 it does not block on a NEW network RTT
 * for every emulated frame, allowing smooth FPS when stable one-way latency
 * fits inside the negotiated input delay. This is not rollback/prediction.
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
    private val onReceiveRom: (String, ByteArray) -> Boolean = { _, _ -> false },
    /** Host-selected cheat codes, never changed while a session is running. */
    private val hostCheatCodes: List<String> = emptyList(),
    /** Both players explicitly opt-in before entering a cheated match. */
    private val allowOnlineCheats: Boolean = false,
    /** Runs only during the pre-frame handshake, after netplay mode is set. */
    private val applyOnlineCheats: (Array<String>) -> Int = { codes ->
        if (codes.isEmpty()) 0 else -6
    },
    /** Session-only and validated: client temporarily applies host core options. */
    private val applyHostCoreOptions: (Map<String, String>) -> Boolean = { false }
) {
    companion object {
        private const val MAGIC = 0x4D5A4E50 // MZNP
        private const val VERSION = 5
        private const val MAX_ROM = 64 * 1024 * 1024
        private const val MAX_STATE = 64 * 1024 * 1024 + 28
        // Avoid heavy full-state snapshots every 2 seconds on slow ARM SoCs.
        // Still verify both emulators deterministically every ~5 seconds.
        private const val HASH_INTERVAL = 300
        private const val PING_MAGIC = 0x4d5a5049 // MZPI
        private const val BUFFER_MAGIC = 0x4d5a4246 // MZBF
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
            // The host controls room lifetime; do not kill a valid LAN room after 60s.
            // Calling stop() closes the listener and unblocks accept().
            listener.soTimeout = 0
            val romHash = romDigest(rom).take(8).joinToString("") {
                "%02x".format(it)
            }
            announcement = LanDiscovery.announce(port, rom.nameWithoutExtension,
                romHash, allowOnlineCheats && hostCheatCodes.isNotEmpty())
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
            connection.tcpNoDelay = true
            connection.keepAlive = true
            onStatus("Conectando ao anfitrião $host:$port…")
            connection.connect(InetSocketAddress(host.trim(), port), 10000)
            runSession(connection, host = false)
        }
    }

    /**
     * Outbound TCP relay transport. Both endpoints connect to the same public
     * server; no inbound ports on either phone, so NAT/CGNAT works. The relay
     * sends READY before the MZNP v4 handshake, including core option sync.
     */
    fun hostViaRelay(serverHost: String, relayPort: Int, roomId: String, title: String) = launch {
        require(rom?.isFile == true) { "O anfitrião precisa abrir uma ROM." }
        val safeTitle = android.util.Base64.encodeToString(
            title.take(75).toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or
                android.util.Base64.NO_PADDING)
        val fingerprint = romDigest(rom).take(8)
            .joinToString("") { "%02x".format(it) }
        val cheats = if (allowOnlineCheats && hostCheatCodes.isNotEmpty()) "1" else "0"
        relayConnect(serverHost, relayPort,
            "HOST $roomId $safeTitle $fingerprint $cheats", true, roomId)
    }

    fun joinViaRelay(serverHost: String, relayPort: Int, roomId: String) = launch {
        require(rom?.isFile == true || receiveAllowedByClient) {
            "Abra a mesma ROM para entrar na sala."
        }
        relayConnect(serverHost, relayPort, "JOIN $roomId", false, roomId)
    }

    private fun relayConnect(
        serverHost: String, relayPort: Int, hello: String,
        hosting: Boolean, roomId: String
    ) {
        require(serverHost.isNotBlank() && serverHost.length <= 253 &&
            relayPort in 1..65535) { "Servidor ou porta inválida." }
        require(roomId.matches(Regex("[a-f0-9]{12,24}"))) { "Código de sala inválido." }
        prepare(true)
        Socket().use { connection ->
            socket = connection
            connection.tcpNoDelay = true
            onStatus(if (hosting) "Criando sala $roomId no servidor…" else
                "Entrando na sala $roomId…")
            connection.connect(InetSocketAddress(serverHost, relayPort), 10000)
            connection.soTimeout = if (hosting) 310000 else 20000
            connection.getOutputStream().write((hello + "\n").toByteArray(Charsets.UTF_8))
            connection.getOutputStream().flush()
            if (hosting) onStatus("Sala $roomId disponível na lista do servidor. Aguardando P2…")
            // Read exactly the line, not a buffered reader that could steal
            // the beginning of the following binary MZNP handshake.
            val reply = StringBuilder()
            while (reply.length < 80) {
                val char = connection.getInputStream().read()
                require(char >= 0) { "Servidor desconectado." }
                if (char == 10) break
                reply.append(char.toChar())
            }
            require(reply.toString() == "READY") {
                "Servidor não conectou a sala: $reply"
            }
            connection.soTimeout = 20000
            onStatus("Túnel TCP estabelecido. Sincronizando emuladores…")
            runSession(connection, host = hosting)
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

    private fun optionsDigest(options: Map<String, String> = coreOptions): ByteArray {
        // Core source is pinned by scripts/fetch_snes9x.sh. No binary identity
        // guarantee across APKs; tested compatibility is still required.
        val manifest = buildString {
            append("MaizoSNES-Snes9x-921f9f7b83660eb44ad263022a57a4a029057c37-mznp5\n")
            options.toSortedMap().forEach { (key, value) ->
                append(key).append('=').append(value).append('\n')
            }
        }
        return digest(manifest.toByteArray(Charsets.UTF_8))
    }

    private fun read32(input: DataInputStream): ByteArray =
        ByteArray(32).also { input.readFully(it) }

    /** MZNP v3: fully synchronized host-authoritative cheat list.
     * Codes must be identically installed on BOTH cores before save-state
     * synchronization and before the first game frame. If either player
     * does not consent or applying fails, abort, never silently desync.
     */
    private fun negotiateOnlineCheats(
        input: DataInputStream,
        output: DataOutputStream,
        isHost: Boolean
    ) {
        if (isHost) {
            val codes = if (allowOnlineCheats) hostCheatCodes else emptyList()
            require(codes.size <= 64) { "Limite de 64 cheats sincronizados." }
            require(codes.all(::validCheatCode)) { "Cheat inválido no host." }
            output.writeInt(codes.size)
            codes.forEach { output.writeUTF(it) }
            output.flush()
            require(input.readBoolean()) {
                "Jogador 2 não autorizou ou não conseguiu aplicar cheats."
            }
            val result = runCatching {
                applyOnlineCheats(codes.toTypedArray())
            }.getOrDefault(-99)
            output.writeBoolean(result >= 0)
            output.flush()
            require(result >= 0) { "Host não conseguiu ativar cheats: erro $result." }
            if (codes.isNotEmpty())
                onStatus("${codes.size} cheats aplicados aos dois jogadores antes da partida.")
        } else {
            val count = input.readInt()
            require(count in 0..64) { "Quantidade de cheats inválida." }
            val codes = (0 until count).map { input.readUTF() }
            require(codes.all(::validCheatCode)) { "Servidor enviou cheats inválidos." }
            if (codes.isNotEmpty() && !allowOnlineCheats) {
                output.writeBoolean(false)
                output.flush()
                error("Host utiliza cheats; ative a autorização em Multiplayer ou jogue sem cheats.")
            }
            val applied = runCatching {
                applyOnlineCheats(codes.toTypedArray())
            }.getOrDefault(-99)
            output.writeBoolean(applied >= 0)
            output.flush()
            require(applied >= 0) {
                "Não foi possível aplicar cheats online (erro $applied)."
            }
            require(input.readBoolean()) { "Host não conseguiu ativar os mesmos cheats." }
            if (codes.isNotEmpty())
                onStatus("${codes.size} cheats sincronizados e autorizados antes do jogo.")
        }
    }

    private fun validCheatCode(code: String): Boolean =
        code.length in 4..190 &&
            code.all { char ->
                char in '0'..'9' || char in 'a'..'f' ||
                    char in 'A'..'F' || char in "-:+,.; "
            }

    private fun runSession(connection: Socket, host: Boolean) {
        connection.tcpNoDelay = true
        connection.keepAlive = true
        runCatching { connection.trafficClass = 0x10 } // IPTOS_LOWDELAY best-effort
        connection.soTimeout = 20000
        // Bounded buffers avoid per-frame allocations while preserving TCP_NODELAY.
        // A buffered output stream flushes once per complete packet below.
        val input = DataInputStream(BufferedInputStream(connection.getInputStream(), 8192))
        val output = DataOutputStream(BufferedOutputStream(connection.getOutputStream(), 4096))
        val ownRom = romDigest(rom)
        // A client computes its settings fingerprint AFTER applying host choices.
        if (host) {
            val ownOptions = optionsDigest()
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.write(ownRom)
            output.write(ownOptions)
            output.writeUTF(rom!!.name.take(110))
            require(coreOptions.size <= 64) { "Opções de núcleo demais." }
            output.writeInt(coreOptions.size)
            coreOptions.toSortedMap().forEach { (key, value) ->
                require(key.length in 1..100 && value.length in 1..100) {
                    "Opção de núcleo inválida."
                }
                output.writeUTF(key)
                output.writeUTF(value)
            }
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
            val optionCount = input.readInt()
            require(optionCount in 0..64) { "Registro de opções inválido." }
            val serverOptions = LinkedHashMap<String, String>()
            repeat(optionCount) {
                val key = input.readUTF()
                val value = input.readUTF()
                val definition = CoreOptionDefinitions.all.firstOrNull { it.key == key }
                require(definition != null && value in definition.values &&
                    serverOptions.put(key, value) == null) {
                    "O host anunciou uma opção não autorizada."
                }
            }
            require(MessageDigest.isEqual(optionsDigest(serverOptions), hostOptions)) {
                "Configuração do host corrompida na rede."
            }
            if (serverOptions != coreOptions) {
                onStatus("Sincronizando configurações do núcleo do anfitrião…")
                require(applyHostCoreOptions(serverOptions)) {
                    "Não foi possível adotar opções do host. Configurações locais preservadas."
                }
                onStatus("Opções do anfitrião aplicadas temporariamente nesta sessão.")
            }
            val ownOptions = optionsDigest(serverOptions)

            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.write(ownRom)
            output.write(ownOptions)
            output.writeBoolean(receiveAllowedByClient)
            output.flush()

            require(MessageDigest.isEqual(hostOptions, ownOptions)) {
                "Falha ao sincronizar configurações do núcleo."
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

        negotiateOnlineCheats(input, output, host)
        onStatus("ROM, opções e cheats conferidos. Sincronizando estado inicial…")
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

        connection.soTimeout = if (mode == NetworkMode.INTERNET ||
            mode == NetworkMode.RELAY || mode == NetworkMode.AUTO) 12000 else 5000
        val period = framePeriodNs().coerceIn(10000000L, 50000000L)
        // One RTT measurement before playback rather than one round trip
        // PER FRAME. Host chooses the shared frame-ahead delay. A slow relay
        // needs more input latency, but no longer limits FPS to 1 / RTT.
        val rttSamples = LongArray(3)
        val delay: Int
        if (host) {
            repeat(rttSamples.size) { index ->
                val sent = System.nanoTime()
                output.writeInt(PING_MAGIC)
                output.writeLong(sent)
                output.flush()
                check(input.readInt() == PING_MAGIC && input.readLong() == sent) {
                    "Falha na medição da latência."
                }
                rttSamples[index] = (System.nanoTime() - sent).coerceAtLeast(0L)
            }
            delay = NetplayPacing.suggestFrameBuffer(
                rttSamples, period, mode)
            output.writeInt(BUFFER_MAGIC)
            output.writeInt(delay)
            output.flush()
            require(input.readBoolean()) { "Cliente não aceitou buffer de rede." }
        } else {
            repeat(rttSamples.size) {
                require(input.readInt() == PING_MAGIC) {
                    "Host não enviou medição RTT."
                }
                val token = input.readLong()
                output.writeInt(PING_MAGIC)
                output.writeLong(token)
                output.flush()
            }
            require(input.readInt() == BUFFER_MAGIC) {
                "Host não enviou configuração de buffer."
            }
            delay = input.readInt()
            require(delay in 2..48) { "Buffer remoto fora do limite: $delay." }
            output.writeBoolean(true)
            output.flush()
        }
        onStatus((if (host) "Conectado como jogador 1 (host)." else
            "Conectado como jogador 2.") +
            " Buffer de $delay quadros (MZNP v5).")
        // Bootstrap both peers with identical button samples for frames
        // 0..delay-1. Then send frame+delay BEFORE consuming peer frame,
        // keeping the TCP stream pipelined and bounded by 'delay'.
        val localInputs = IntArray(delay) { localInput() and 0xffff }
        for (initialFrame in 0 until delay) {
            output.writeInt(initialFrame)
            output.writeInt(localInputs[initialFrame])
            output.writeInt(-1) // No state checkpoint yet.
            output.writeLong(0L)
        }
        output.flush()
        var nextFrameAt = System.nanoTime()
        var frame = 0
        var measureFrames = 0
        var measureStalls = 0L
        var measureWaitNs = 0L
        var measureMaxWaitNs = 0L
        var measureStart = System.nanoTime()
        // Checkpoint for the *finished* emulation frame. Written alongside
        // a future INPUT packet, not as a separate in-band message that would
        // corrupt the already queued future frame packets.
        val localCheckpoints = HashMap<Int, Long>(3)
        var lastCheckpointFrame = -1
        var lastCheckpointHash = 0L
        while (!closed.get()) {
            val ringSlot = frame % delay
            val localMask = localInputs[ringSlot]
            val futureMask = localInput() and 0xffff
            localInputs[ringSlot] = futureMask
            val waitStart = System.nanoTime()
            // Publish future inputs early; not one request/response per frame.
            output.writeInt(frame + delay)
            output.writeInt(futureMask)
            output.writeInt(lastCheckpointFrame)
            output.writeLong(lastCheckpointHash)
            output.flush()
            val peerFrame = input.readInt()
            require(peerFrame == frame) { "Frame inválido: esperado $frame, veio $peerFrame." }
            val peerMask = input.readInt() and 0xffff
            val remoteCheckpointFrame = input.readInt()
            val remoteCheckpointHash = input.readLong()
            require(remoteCheckpointFrame == -1 ||
                (remoteCheckpointFrame >= 0 &&
                    remoteCheckpointFrame % HASH_INTERVAL == 0 &&
                    remoteCheckpointFrame < frame)) {
                "Checkpoint remoto fora de ordem."
            }
            if (remoteCheckpointFrame >= 0) {
                val local = localCheckpoints[remoteCheckpointFrame]
                if (local != null) require(local == remoteCheckpointHash) {
                    "Dessíncronia no quadro $remoteCheckpointFrame."
                }
            }
            val waitNs = (System.nanoTime() - waitStart).coerceAtLeast(0L)
            measureFrames++
            measureWaitNs += waitNs
            measureMaxWaitNs = maxOf(measureMaxWaitNs, waitNs)
            if (waitNs > period) measureStalls++
            val p1 = if (host) localMask else peerMask
            val p2 = if (host) peerMask else localMask
            check(step(p1, p2) == 0) { "Erro ao processar quadro $frame." }
            if (frame % HASH_INTERVAL == 0) {
                lastCheckpointHash = stateHash()
                lastCheckpointFrame = frame
                localCheckpoints[frame] = lastCheckpointHash
                // Keep a short 2-checkpoint ring; remote input lead <= 48.
                if (localCheckpoints.size > 2)
                    localCheckpoints.remove(frame - 2 * HASH_INTERVAL)
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
                    stalledFrames = measureStalls,
                    inputDelayFrames = delay,
                    sampledRttMs = if (host) rttSamples.maxOrNull()!!.toDouble() / 1e6 else 0.0
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
