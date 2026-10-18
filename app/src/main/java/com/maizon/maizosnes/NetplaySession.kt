package com.maizon.maizosnes

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Two-player experimental deterministic TCP lockstep.
 *
 * Host owns P1 and the initial serialized state; client owns P2. Both peers run
 * exactly one frame only after exchanging the two inputs for that frame.
 * This deliberately trades latency for correctness. Rollback is not implemented.
 * Transport works on LAN or over a manually reachable internet TCP endpoint;
 * neither encryption, matchmaking nor NAT traversal is provided.
 */
internal class NetplaySession(
    private val rom: File,
    private val coreOptions: Map<String, String>,
    private val localInput: () -> Int,
    private val prepare: (Boolean) -> Unit,
    private val saveState: () -> ByteArray?,
    private val loadState: (ByteArray) -> Int,
    private val step: (Int, Int) -> Int,
    private val stateHash: () -> Long,
    private val framePeriodNs: () -> Long,
    private val onStatus: (String) -> Unit,
    private val onFinish: (String) -> Unit
) {
    companion object {
        private const val MAGIC = 0x4D5A4E50 // MZNP
        private const val VERSION = 1
        private const val MAX_STATE = 64 * 1024 * 1024 + 28
        private const val HASH_INTERVAL = 120
    }

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var thread: Thread? = null

    fun host(port: Int) = launch {
        require(port in 1024..65535) { "Porta deve estar entre 1024 e 65535." }
        prepare(true)
        ServerSocket(port).use { listener ->
            server = listener
            listener.soTimeout = 60000
            onStatus("Sala aberta na porta $port. Aguardando jogador 2…")
            listener.accept().use { connection ->
                server = null
                socket = connection
                runSession(connection, host = true)
            }
        }
    }

    fun join(host: String, port: Int) = launch {
        require(host.isNotBlank() && host.length <= 253) { "Informe o IP ou domínio do anfitrião." }
        require(port in 1024..65535) { "Porta inválida." }
        prepare(true)
        Socket().use { connection ->
            socket = connection
            onStatus("Conectando a $host:$port…")
            connection.connect(InetSocketAddress(host.trim(), port), 10000)
            runSession(connection, host = false)
        }
    }

    private fun launch(action: () -> Unit) {
        check(thread == null) { "A sessão já foi iniciada." }
        thread = Thread({
            var message = "Sessão encerrada."
            try {
                action()
            } catch (error: Exception) {
                message = if (closed.get()) "Sessão encerrada pelo usuário."
                else "Conexão encerrada: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                closed.set(true)
                runCatching { socket?.close() }
                runCatching { server?.close() }
                runCatching { prepare(false) }
                onFinish(message)
            }
        }, "Maizo-Netplay").also { it.start() }
    }

    fun stop() {
        closed.set(true)
        runCatching { socket?.close() }
        runCatching { server?.close() }
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun romDigest(): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        rom.inputStream().buffered().use { input ->
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
        // Includes a protocol-level core identifier; different APK/core builds
        // are NOT guaranteed deterministic and must be tested on actual devices.
        val manifest = buildString {
            append("MaizoSNES-Snes9x-1.63-libretro-v1\n")
            coreOptions.toSortedMap().forEach { (key, value) ->
                append(key).append('=').append(value).append('\n')
            }
        }
        return digest(manifest.toByteArray(Charsets.UTF_8))
    }

    private fun runSession(connection: Socket, host: Boolean) {
        connection.tcpNoDelay = true
        connection.keepAlive = true
        connection.soTimeout = 20000
        val input = DataInputStream(BufferedInputStream(connection.getInputStream(), 32768))
        val output = DataOutputStream(BufferedOutputStream(connection.getOutputStream(), 32768))
        val ownRom = romDigest()
        val ownOptions = optionsDigest()
        if (host) {
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.write(ownRom)
            output.write(ownOptions)
            output.flush()
        }
        val peerMagic = input.readInt()
        val peerVersion = input.readInt()
        require(peerMagic == MAGIC && peerVersion == VERSION) {
            "Protocolo incompatível. Atualize os dois aparelhos."
        }
        val peerRom = ByteArray(32).also(input::readFully)
        val peerOptions = ByteArray(32).also(input::readFully)
        require(MessageDigest.isEqual(peerRom, ownRom)) {
            "As ROMs diferem (SHA-256). Os dois jogadores precisam da mesma cópia."
        }
        require(MessageDigest.isEqual(peerOptions, ownOptions)) {
            "Opções do núcleo diferentes. Iguale as configurações nos dois aparelhos."
        }
        if (!host) {
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.write(ownRom)
            output.write(ownOptions)
            output.flush()
        }
        onStatus("ROM e opções verificadas; sincronizando estado inicial…")
        if (host) {
            val state = saveState() ?: error("Não foi possível salvar o estado inicial.")
            require(state.size in 29..MAX_STATE) { "Estado inicial inválido." }
            output.writeInt(state.size)
            output.write(state)
            output.flush()
            require(input.readUnsignedByte() == 1) { "O segundo aparelho recusou o estado." }
        } else {
            val length = input.readInt()
            require(length in 29..MAX_STATE) { "Estado remoto maior que o limite ou inválido." }
            val state = ByteArray(length)
            input.readFully(state)
            val result = loadState(state)
            if (result != 0) {
                output.writeByte(0)
                output.flush()
                error("Falha ao restaurar estado inicial ($result).")
            }
            output.writeByte(1)
            output.flush()
        }
        // No further core resets/options/state imports allowed until disconnection.
        connection.soTimeout = 5000
        onStatus(if (host) "Conectado como jogador 1 (host)." else "Conectado como jogador 2.")
        val period = framePeriodNs().coerceIn(10000000L, 50000000L)
        var nextFrameAt = System.nanoTime()
        var frame = 0
        while (!closed.get()) {
            val mask = localInput() and 0xffff
            output.writeInt(frame)
            output.writeInt(mask)
            output.flush()
            val peerFrame = input.readInt()
            require(peerFrame == frame) { "Quadro inesperado: esperado $frame, recebido $peerFrame." }
            val peerMask = input.readInt() and 0xffff
            val p1 = if (host) mask else peerMask
            val p2 = if (host) peerMask else mask
            check(step(p1, p2) == 0) { "Erro ao executar quadro sincronizado $frame." }
            if (frame % HASH_INTERVAL == 0) {
                val localHash = stateHash()
                output.writeLong(localHash)
                output.flush()
                val remoteHash = input.readLong()
                check(localHash == remoteHash) {
                    "Dessíncronia detectada no quadro $frame. Sessão interrompida."
                }
            }
            frame++
            nextFrameAt += period
            val waitNs = nextFrameAt - System.nanoTime()
            if (waitNs > 0) LockSupport.parkNanos(waitNs)
            else if (waitNs < -period * 3) nextFrameAt = System.nanoTime()
        }
    }
}
