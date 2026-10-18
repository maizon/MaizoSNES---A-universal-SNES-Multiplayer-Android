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

    fun load(context: Context, file: File): Bitmap? {
        val knownName = runCatching { RomDatabase.lookup(context, file)?.title }.getOrNull()
        val title = file.nameWithoutExtension
        val candidates = listOfNotNull(knownName, title,
            title.substringBefore('(').trim().takeIf { it.length > 4 })
            .distinct().take(3)
        val cache = File(context.filesDir, "covers").apply { mkdirs() }
        for (candidate in candidates) {
            val key = MessageDigest.getInstance("SHA-256")
                .digest(candidate.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val destination = File(cache, "$key.png")
            if (destination.isFile && destination.length() in 1L..MAX_IMAGE.toLong()) {
                decodeSmall(destination.readBytes())?.let { return it }
            }
            val normalized = candidate.replace(Regex("[&*/:\"<>?\\\\|]"), "_")
            val url = BASE + Uri.encode(normalized) + ".png"
            val image = runCatching { fetchPng(url) }.getOrNull() ?: continue
            val bitmap = decodeSmall(image) ?: continue
            runCatching {
                File(cache, "$key.part").apply { writeBytes(image) }.renameTo(destination)
            }
            return bitmap
        }
        return null
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
