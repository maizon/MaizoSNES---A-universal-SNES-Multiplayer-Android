package com.maizon.maizosnes

import java.io.File
import java.io.RandomAccessFile

/**
 * Offline SNES internal-header reader. A name/header is not a trusted checksum.
 * Preserves filename as fallback for homebrew and unusual mappings.
 */
internal object SnesRomHeader {
    data class Details(val title: String, val region: String, val warning: String?)

    fun inspect(file: File): Details {
        val fallback = Details(file.nameWithoutExtension, "Desconhecida",
            "Cabeçalho não reconhecido; confira a ROM e o checksum no catálogo No-Intro.")
        if (!file.isFile || file.length() < 0x8000L || file.length() > 64L * 1024 * 1024)
            return fallback.copy(warning = "ROM pequena, vazia ou maior que 64 MiB.")
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val copier = if (input.length() % 1024L == 512L) 512L else 0L
                val positions = listOf(0x7FC0L, 0xFFC0L, 0x40FFC0L)
                val candidates = positions.mapNotNull { position ->
                    if (position + copier + 32 > input.length()) return@mapNotNull null
                    val header = ByteArray(32)
                    input.seek(position + copier)
                    input.readFully(header)
                    val raw = header.copyOfRange(0, 21)
                    val title = String(raw, Charsets.US_ASCII).trim(' ', '\u0000')
                    if (title.length < 4 || title.any { it.code !in 32..126 }) return@mapNotNull null
                    val complement = (header[28].toInt() and 255) or
                        ((header[29].toInt() and 255) shl 8)
                    val checksum = (header[30].toInt() and 255) or
                        ((header[31].toInt() and 255) shl 8)
                    val validChecksum = (complement xor checksum) == 0xFFFF
                    val score = title.count { it.isLetterOrDigit() } +
                        if (validChecksum) 64 else 0
                    val regionCode = header[25].toInt() and 255
                    Triple(title, regionCode, score to validChecksum)
                }
                val chosen = candidates.maxByOrNull { it.third.first } ?: return@use fallback
                val region = when (chosen.second) {
                    0 -> "Japão"
                    1 -> "América do Norte"
                    2 -> "Europa"
                    3 -> "Suécia"
                    4 -> "Finlândia"
                    5 -> "Dinamarca"
                    6 -> "França"
                    7 -> "Países Baixos"
                    8 -> "Espanha"
                    9 -> "Alemanha"
                    10 -> "Itália"
                    11 -> "Hong Kong / China"
                    12 -> "Indonésia"
                    13 -> "Coreia"
                    else -> "Região " + chosen.second
                }
                Details(chosen.first, region,
                    if (chosen.third.second) null else
                        "Checksum do header não verificado. Isso não prova corrupção: consulte a identificação exata.")
            }
        }.getOrDefault(fallback)
    }
}
