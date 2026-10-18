package com.maizon.maizosnes

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.CRC32

internal data class RomIdentity(
    val title: String,
    val region: String?,
    val crc32: String,
    val sha1: String,
    val exactChecksum: Boolean
)

/**
 * Exact game identification using CRC32 + size + SHA-1 from pinned
 * libretro-database No-Intro metadata. Optional 512-byte copier header
 * is removed from the hashing window; original ROM remains unmodified.
 * A title match alone never claims to be checksum verified.
 */
internal object RomDatabase {
    private const val COMMIT = "12c21cf79f1e20eff6ac68b80875816c717d0a7a"
    private const val FILE = "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System.dat"
    private const val URL_DAT =
        "https://raw.githubusercontent.com/libretro/libretro-database/$COMMIT/metadat/no-intro/$FILE"
    private const val MAX_DAT_BYTES = 1_800_000
    private val lineName = Regex("^\\s*name \"([^\"]{1,180})\"")
    private val regionLine = Regex("^\\s*region \"([^\"]{1,80})\"")
    private val romMeta = Regex("\\bsize\\s+(\\d+)\\s+crc\\s+([0-9A-Fa-f]{8}).*?\\bsha1\\s+([0-9A-Fa-f]{40})", RegexOption.IGNORE_CASE)

    internal data class Fingerprint(val size: Long, val crc: String, val sha1: String)

    internal fun fingerprint(file: File): Fingerprint {
        val size = file.length()
        require(size > 0 && size <= 64L * 1024 * 1024) { "ROM de tamanho inválido." }
        val copier = if (size % 1024 == 512L) 512L else 0L
        val crc32 = CRC32()
        val sha = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { stream ->
            if (copier > 0) check(stream.skip(copier) == copier) { "Cabeçalho incompleto." }
            val buffer = ByteArray(65536)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                crc32.update(buffer, 0, count)
                sha.update(buffer, 0, count)
            }
        }
        return Fingerprint(size - copier,
            java.lang.Long.toHexString(crc32.value).uppercase().padStart(8, '0'),
            sha.digest().joinToString("") { "%02X".format(it) })
    }

    internal fun parseMatch(dat: String, hash: Fingerprint): RomIdentity? {
        var title: String? = null
        var region: String? = null
        for (line in dat.lineSequence()) {
            if (line.trim() == "game (") {
                title = null
                region = null
                continue
            }
            if (title == null) title = lineName.find(line)?.groupValues?.get(1)
            region = region ?: regionLine.find(line)?.groupValues?.get(1)
            if (!line.contains("rom (")) continue
            val meta = romMeta.find(line) ?: continue
            val size = meta.groupValues[1].toLongOrNull() ?: continue
            val crc = meta.groupValues[2].uppercase()
            val sha1 = meta.groupValues[3].uppercase()
            if (size == hash.size && crc == hash.crc && sha1 == hash.sha1) {
                return RomIdentity(title ?: "SNES", region, crc, sha1, true)
            }
        }
        return null
    }

    fun lookup(context: Context, rom: File, refresh: Boolean = false): RomIdentity? {
        val hash = fingerprint(rom)
        val directory = File(context.filesDir, "game_metadata").apply { mkdirs() }
        val datFile = File(directory, "no-intro-$COMMIT.dat")
        val dat = if (!refresh && datFile.isFile &&
            datFile.length() in 1000L..MAX_DAT_BYTES.toLong()) {
            datFile.readText(Charsets.UTF_8)
        } else {
            val text = downloadDat()
            val temp = File(directory, "metadata.part")
            temp.writeText(text, Charsets.UTF_8)
            if (datFile.exists()) datFile.delete()
            check(temp.renameTo(datFile)) { "Falha ao salvar base CRC." }
            text
        }
        return parseMatch(dat, hash)
    }

    private fun downloadDat(): String {
        val connection = (URL(URL_DAT).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "MaizoSNES-Metadata")
        }
        try {
            check(connection.responseCode == 200) { "Metadados indisponíveis: HTTP ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= MAX_DAT_BYTES) { "Arquivo de metadados excede limite." }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { connection.disconnect() }
    }
}
