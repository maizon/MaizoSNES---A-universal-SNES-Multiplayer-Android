package com.maizon.maizosnes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Optional, on-demand boxart cache. Images are retrieved to the user's
 * app-private storage; no game image is committed or packed into the APK.
 * Covers may have separate copyrights and are not licensed by emulator code.
 */
internal object CoverArt {
    private const val BASE =
        "https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/"
    private const val MAX_IMAGE = 2_500_000

    private const val CATALOG_MAX_BYTES = 2_500_000
    private const val CATALOG_TTL_MS = 7L * 24 * 60 * 60 * 1000

    fun load(context: Context, file: File): Bitmap? {
        if (!file.isFile) return null
        val knownName = runCatching { RomDatabase.lookup(context, file)?.title }.getOrNull()
        val headerTitle = runCatching { SnesRomHeader.inspect(file).title }.getOrNull()
        val filename = file.nameWithoutExtension
        val aliases = listOfNotNull(knownName, headerTitle, filename,
            filename.substringBefore('(').substringBefore('[').takeIf { it.length > 5 })
            .distinct().filter { it.length in 5..150 }
        val cache = File(context.filesDir, "covers").apply { mkdirs() }

        // Reuse both the old title-keyed cache and the new filename-keyed cache.
        val keys = listOf(filename) + aliases
        keys.distinct().forEach { alias ->
            val destination = File(cache, hash(alias) + ".png")
            if (destination.isFile && destination.length() in 1L..MAX_IMAGE.toLong()) {
                val image = runCatching { decodeSmall(destination.readBytes()) }.getOrNull()
                if (image != null) return image
            }
        }

        val catalog = readCatalog(cache)
        val exactName = CoverTitleMatcher.bestCover(aliases, catalog)
        val candidates = listOfNotNull(exactName) +
            CoverTitleMatcher.directCandidates(aliases).map { "$it.png" }
        for (candidate in candidates.distinct().take(6)) {
            val safe = candidate.removeSuffix(".png")
                .replace(Regex("""[&*/:"<>?\\|]"""), "_")
            val url = BASE + Uri.encode(safe) + ".png"
            val data = runCatching { fetchPng(url) }.getOrNull() ?: continue
            val bitmap = decodeSmall(data) ?: continue
            runCatching {
                val destination = File(cache, hash(filename) + ".png")
                val part = File(cache, hash(filename) + ".part")
                part.writeBytes(data)
                if (!part.renameTo(destination)) {
                    part.delete()
                }
            }
            return bitmap
        }
        return null
    }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun readCatalog(cache: File): List<String> {
        val index = File(cache, "libretro_snes_boxarts.txt")
        val now = System.currentTimeMillis()
        if (index.isFile && index.length() in 1L..CATALOG_MAX_BYTES.toLong() &&
            now - index.lastModified() < CATALOG_TTL_MS) {
            return runCatching { index.readLines() }.getOrDefault(emptyList())
        }
        val connection = (URL(BASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 5000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "MaizoSNES-CoverArt/1.0")
        }
        return try {
            if (connection.responseCode != 200 ||
                !connection.contentType.orEmpty().contains("text/html", true)) {
                if (index.isFile) return index.readLines()
                return emptyList()
            }
            val bytes = connection.inputStream.use { input ->
                val data = ByteArrayOutputStream()
                val block = ByteArray(16384)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    if (data.size() + count > CATALOG_MAX_BYTES) return emptyList()
                    data.write(block, 0, count)
                }
                data.toByteArray()
            }
            val filenames = CoverTitleMatcher.namesFromIndex(bytes.toString(Charsets.UTF_8))
            if (filenames.size > 100) {
                runCatching { index.writeText(filenames.joinToString("\n")) }
            }
            filenames
        } catch (_: Exception) {
            runCatching { if (index.isFile) index.readLines() else emptyList() }
                .getOrDefault(emptyList())
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchPng(url: String): ByteArray {
        require(url.startsWith(BASE)) { "Origem de capa inválida." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 10000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "MaizoSNES-CoverArt")
        }
        try {
            check(connection.responseCode == 200) { "Sem capa correspondente." }
            val mime = connection.contentType.orEmpty()
            require(mime.contains("image/png", true) || mime.contains("application/octet-stream", true)) {
                "Capa não é PNG."
            }
            return connection.inputStream.use { input ->
                val data = ByteArrayOutputStream()
                val buf = ByteArray(16384)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    require(data.size() + n <= MAX_IMAGE) { "Capa excede limite." }
                    data.write(buf, 0, n)
                }
                data.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        if (bytes.size < 8 ||
            !(bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte())) return null
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        if (probe.outWidth !in 1..4096 || probe.outHeight !in 1..4096) return null
        var sample = 1
        while (probe.outWidth / sample > 300 || probe.outHeight / sample > 430) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
