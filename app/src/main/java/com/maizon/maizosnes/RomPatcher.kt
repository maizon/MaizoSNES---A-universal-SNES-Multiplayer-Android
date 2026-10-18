package com.maizon.maizosnes

import java.util.zip.CRC32

/**
 * Applies user-selected IPS/BPS ROM modifications. This code ships no game
 * patches, ROMs or copyrighted game data. The original ROM is never overwritten.
 */
internal object RomPatcher {
    private const val MAX_ROM = 64 * 1024 * 1024

    fun apply(original: ByteArray, patch: ByteArray): ByteArray {
        require(original.isNotEmpty() && original.size <= MAX_ROM) { "ROM original inválida." }
        return when {
            patch.size >= 8 && String(patch, 0, 5, Charsets.US_ASCII) == "PATCH" ->
                applyIps(original, patch)
            patch.size >= 19 && String(patch, 0, 4, Charsets.US_ASCII) == "BPS1" ->
                applyBps(original, patch)
            else -> error("Patch não é IPS ou BPS válido.")
        }
    }

    private class Reader(val data: ByteArray, var at: Int = 0) {
        fun byte(): Int {
            require(at < data.size) { "Patch truncado." }
            return data[at++].toInt() and 255
        }
        fun uint16(): Int = byte() shl 8 or byte()
        fun uint24(): Int = byte() shl 16 or (byte() shl 8) or byte()
        fun variable(): Long {
            var value = 0L
            var shift = 1L
            repeat(10) {
                val b = byte()
                value += (b and 127) * shift
                require(value >= 0 && value <= MAX_ROM.toLong() * 8L) {
                    "Valor fora do intervalo permitido."
                }
                if (b and 128 != 0) return value
                shift = shift shl 7
                value += shift
            }
            error("Valor variável inválido.")
        }
        fun signed(): Long {
            val value = variable()
            return if (value and 1L != 0L) -(value shr 1) else value shr 1
        }
    }

    private fun applyIps(source: ByteArray, patch: ByteArray): ByteArray {
        val r = Reader(patch, 5)
        var output = source.copyOf()
        var eof = false
        while (r.at + 3 <= patch.size) {
            if (patch[r.at] == 'E'.code.toByte() &&
                patch[r.at + 1] == 'O'.code.toByte() &&
                patch[r.at + 2] == 'F'.code.toByte()) {
                r.at += 3
                eof = true
                break
            }
            val offset = r.uint24()
            val length = r.uint16()
            val count: Int
            val value: Int
            if (length == 0) {
                count = r.uint16()
                value = r.byte()
            } else {
                count = length
                value = -1
                require(r.at + count <= patch.size) { "Dados IPS incompletos." }
            }
            require(offset.toLong() + count <= MAX_ROM) { "Patch excede limite de ROM." }
            if (offset + count > output.size) output = output.copyOf(offset + count)
            if (length == 0) {
                output.fill(value.toByte(), offset, offset + count)
            } else {
                patch.copyInto(output, offset, r.at, r.at + count)
                r.at += count
            }
        }
        require(eof) { "IPS sem marcador EOF." }
        if (r.at + 3 == patch.size) {
            val targetSize = r.uint24()
            require(targetSize in 1..MAX_ROM) { "IPS tamanho final inválido." }
            output = output.copyOf(targetSize)
        }
        require(r.at == patch.size) { "Dados depois do fim do IPS." }
        return output
    }

    private fun crc(data: ByteArray, length: Int): Long {
        val crc = CRC32()
        crc.update(data, 0, length)
        return crc.value
    }
    private fun read32(data: ByteArray, index: Int): Long =
        ((data[index].toLong() and 255L) or
            ((data[index + 1].toLong() and 255L) shl 8) or
            ((data[index + 2].toLong() and 255L) shl 16) or
            ((data[index + 3].toLong() and 255L) shl 24))

    private fun applyBps(source: ByteArray, patch: ByteArray): ByteArray {
        val footer = patch.size - 12
        val sourceChecksum = read32(patch, footer)
        val targetChecksum = read32(patch, footer + 4)
        val patchChecksum = read32(patch, footer + 8)
        require(crc(patch, patch.size - 4) == patchChecksum) {
            "Integridade do BPS inválida."
        }
        require(crc(source, source.size) == sourceChecksum) {
            "BPS incompatível com a versão/checksum da ROM."
        }
        val r = Reader(patch, 4)
        val sourceSize = r.variable()
        val targetSize = r.variable()
        val metadataSize = r.variable()
        require(sourceSize == source.size.toLong() && targetSize in 1..MAX_ROM.toLong()) {
            "BPS com tamanho de ROM incompatível."
        }
        require(metadataSize <= footer - r.at) { "Metadados BPS inválidos." }
        r.at += metadataSize.toInt()
        val output = ByteArray(targetSize.toInt())
        var at = 0
        var sourceCopy = 0L
        var targetCopy = 0L
        while (at < output.size) {
            require(r.at < footer) { "Operações BPS incompletas." }
            val command = r.variable()
            val length = ((command shr 2) + 1L).toInt()
            require(length > 0 && length <= output.size - at) {
                "Comando BPS ultrapassa o tamanho de destino."
            }
            when ((command and 3L).toInt()) {
                0 -> {
                    require(at.toLong() + length <= source.size) {
                        "SourceRead fora da ROM."
                    }
                    source.copyInto(output, at, at, at + length)
                    at += length
                }
                1 -> {
                    require(r.at + length <= footer) { "TargetRead truncado." }
                    patch.copyInto(output, at, r.at, r.at + length)
                    at += length
                    r.at += length
                }
                2 -> {
                    sourceCopy += r.signed()
                    require(sourceCopy >= 0 && sourceCopy + length <= source.size) {
                        "SourceCopy fora da ROM."
                    }
                    source.copyInto(output, at, sourceCopy.toInt(),
                        (sourceCopy + length).toInt())
                    sourceCopy += length
                    at += length
                }
                3 -> {
                    targetCopy += r.signed()
                    require(targetCopy >= 0 && targetCopy < at) { "TargetCopy inválido." }
                    repeat(length) {
                        require(targetCopy < at) { "TargetCopy fora do destino." }
                        output[at++] = output[targetCopy.toInt()]
                        targetCopy++
                    }
                }
            }
        }
        require(r.at == footer) { "Dados extras no BPS." }
        require(crc(output, output.size) == targetChecksum) { "BPS resultado com checksum incorreto." }
        return output
    }
}
