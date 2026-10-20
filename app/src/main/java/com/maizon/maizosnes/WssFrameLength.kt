package com.maizon.maizosnes

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Decode the RFC6455 64-bit length field under the relay's 64 KiB limit. */
internal object WssFrameLength {
    /** Reject non-canonical 16-bit lengths (0..125 must use the short header). */
    fun read16(input: InputStream): Int {
        val hi = input.read()
        val lo = input.read()
        if (hi < 0 || lo < 0) throw EOFException("Tamanho WSS incompleto")
        val length = (hi shl 8) or lo
        if (length < 126) throw IOException("Tamanho WSS de 16 bits não canônico")
        return length
    }

    /** RFC 6455: control frames are unfragmented and have at most 125 bytes. */
    fun validateControl(opcode: Int, length: Int) {
        if (opcode in 8..15) {
            if (length > 125) throw IOException("Quadro de controle WSS excessivo")
            if (opcode == 8 && length == 1) throw IOException("Close WSS inválido")
        }
    }

    fun read64(input: InputStream): Int {
        var size = 0L
        repeat(8) {
            val byte = input.read()
            if (byte < 0) throw EOFException("Tamanho WSS incompleto")
            size = (size shl 8) or byte.toLong()
        }
        if (size != 65536L) throw IOException("Quadro WSS excessivo ou não canônico")
        return 65536
    }
}
