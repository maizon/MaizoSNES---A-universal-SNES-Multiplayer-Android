package com.maizon.maizosnes

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * RFC6455 byte-stream adapter on a genuine CA + hostname verified HTTPS socket.
 * WSS is terminated by the user's HTTPS reverse proxy: TLS is not end-to-end
 * encryption against the relay operator, who can inspect game/ROM bytes.
 * Preserve MZNP framing: no change to the deterministic game protocol.
 */
internal class WssRelaySocket private constructor(
    private val tls: SSLSocket,
    private val input: InputStream,
    private val output: OutputStream
) : Socket() {
    private val lock = Any()
    private val random = SecureRandom()
    private val decoded = object : InputStream() {
        private var remaining = 0
        private var closedByPeer = false
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) -1 else b[0].toInt() and 255
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || len > b.size - off) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            if (closedByPeer) return -1
            while (remaining == 0) {
                val flags = input.read()
                if (flags < 0) return -1
                val second = readByte(input)
                if ((flags and 0x70) != 0 || (flags and 0x80) == 0 ||
                    (second and 0x80) != 0) throw IOException("Quadro WSS inválido")
                val opcode = flags and 15
                val amount = second and 127
                val size = when (amount) {
                    127 -> WssFrameLength.read64(input)
                    126 -> WssFrameLength.read16(input)
                    else -> amount
                }
                if (size > 65536) throw IOException("Quadro WSS excessivo")
                WssFrameLength.validateControl(opcode, size)
                if (opcode == 8) {
                    if (size > 0) readFully(input, ByteArray(size))
                    closedByPeer = true
                    return -1
                }
                if (opcode == 9) {
                    val payload = ByteArray(size)
                    readFully(input, payload)
                    sendFrame(10, payload, 0, payload.size)
                    continue
                }
                if (opcode == 10) {
                    val pong = ByteArray(size)
                    readFully(input, pong)
                    continue
                }
                if (opcode != 2) throw IOException("Quadro WSS não é binário")
                remaining = size
            }
            val count = input.read(b, off, minOf(len, remaining))
            if (count < 0) throw EOFException("Fluxo WSS incompleto")
            if (count == 0) return read(b, off, len)
            remaining -= count
            return count
        }
    }
    private val encoded = object : OutputStream() {
        override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }
        override fun write(bytes: ByteArray, off: Int, len: Int) {
            if (off < 0 || len < 0 || len > bytes.size - off) throw IndexOutOfBoundsException()
            var cursor = off
            val end = off + len
            while (cursor < end) {
                val count = minOf(16384, end - cursor)
                sendFrame(2, bytes, cursor, count)
                cursor += count
            }
        }
        override fun flush() { synchronized(lock) { output.flush() } }
    }

    private fun sendFrame(opcode: Int, data: ByteArray, off: Int, len: Int) {
        synchronized(lock) {
            output.write(0x80 or opcode)
            if (len < 126) output.write(0x80 or len)
            else {
                output.write(0x80 or 126)
                output.write(len ushr 8)
                output.write(len and 255)
            }
            val key = ByteArray(4)
            random.nextBytes(key)
            output.write(key)
            val masked = ByteArray(len)
            for (index in 0 until len) masked[index] =
                (data[off + index].toInt() xor key[index % 4].toInt()).toByte()
            output.write(masked)
            output.flush()
        }
    }

    override fun getInputStream(): InputStream = decoded
    override fun getOutputStream(): OutputStream = encoded
    override fun setTcpNoDelay(on: Boolean) { tls.tcpNoDelay = on }
    override fun getTcpNoDelay(): Boolean = tls.tcpNoDelay
    override fun setSoTimeout(timeout: Int) { tls.soTimeout = timeout }
    override fun getSoTimeout(): Int = tls.soTimeout
    override fun setKeepAlive(on: Boolean) { tls.keepAlive = on }
    override fun getKeepAlive(): Boolean = tls.keepAlive
    override fun setTrafficClass(tc: Int) { tls.trafficClass = tc }
    override fun getTrafficClass(): Int = tls.trafficClass
    override fun getInetAddress() = tls.inetAddress
    override fun getLocalAddress() = tls.localAddress
    override fun getPort(): Int = tls.port
    override fun isClosed(): Boolean = tls.isClosed
    override fun close() { tls.close() }

    companion object {
        private fun readByte(stream: InputStream): Int {
            val value = stream.read()
            if (value < 0) throw EOFException("WSS fechado durante negociação")
            return value
        }
        private fun readFully(stream: InputStream, data: ByteArray) {
            var offset = 0
            while (offset < data.size) {
                val len = stream.read(data, offset, data.size - offset)
                if (len <= 0) throw EOFException("WSS desconectou no meio do quadro")
                offset += len
            }
        }
        fun connect(httpsBase: String): WssRelaySocket {
            val url = RelayDirectory.endpoint(httpsBase)
            require(url.protocol == "https") { "Salas WSS exigem HTTPS real." }
            val host = url.host
            val port = if (url.port == -1) 443 else url.port
            val underlying = SSLSocketFactory.getDefault()
                .createSocket(host, port) as SSLSocket
            try {
                underlying.soTimeout = 10000
                underlying.sslParameters = underlying.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                underlying.startHandshake()
                val randomBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val key = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
                val request = "GET /ws HTTP/1.1\r\n" +
                    "Host: $host" + (if (port == 443) "" else ":$port") + "\r\n" +
                    "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $key\r\n\r\n"
                val output = underlying.outputStream
                output.write(request.toByteArray(Charsets.US_ASCII))
                output.flush()
                val input = underlying.inputStream
                val bytes = java.io.ByteArrayOutputStream()
                var suffix = 0
                while (suffix != 4 && bytes.size() < 8192) {
                    val c = readByte(input)
                    bytes.write(c)
                    suffix = when {
                        suffix == 0 && c == 13 -> 1
                        suffix == 1 && c == 10 -> 2
                        suffix == 2 && c == 13 -> 3
                        suffix == 3 && c == 10 -> 4
                        c == 13 -> 1
                        else -> 0
                    }
                }
                require(suffix == 4) { "Resposta WebSocket excessiva." }
                val lines = bytes.toString("US-ASCII").split("\r\n")
                require(lines.firstOrNull()?.startsWith("HTTP/1.1 101 ") == true) {
                    "Servidor não habilitou WebSocket."
                }
                val headers = lines.drop(1).mapNotNull { line ->
                    val sep = line.indexOf(':')
                    if (sep < 1) null else line.substring(0, sep).lowercase() to
                        line.substring(sep + 1).trim()
                }.toMap()
                val expected = Base64.encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                        (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                            .toByteArray(Charsets.US_ASCII)), Base64.NO_WRAP)
                require(headers["sec-websocket-accept"] == expected &&
                    headers["upgrade"]?.equals("websocket", true) == true) {
                    "Servidor WebSocket não autenticou a atualização."
                }
                return WssRelaySocket(underlying, input, output)
            } catch (error: Exception) {
                underlying.close()
                throw error
            }
        }
    }
}
