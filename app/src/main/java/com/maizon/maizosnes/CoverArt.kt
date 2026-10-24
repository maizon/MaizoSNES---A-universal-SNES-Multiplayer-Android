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
    private const val NES_BASE =
        "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/"
    private const val MAX_IMAGE = 2_500_000

    private const val CATALOG_MAX_BYTES = 2_500_000
    private const val CATALOG_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** User-owned local images are always preferred over optional remote boxart. */
    fun load(context: Context, file: File, allowNetwork: Boolean = true): Bitmap? {
        if (!file.isFile) return null
        val preferred = customFile(context, file)
        if (preferred.isFile && preferred.length() in 1L..MAX_IMAGE.toLong()) {
            runCatching { decodeSmall(preferred.readBytes()) }.getOrNull()?.let { return it }
        }
        val nes = RomSystem.isNes(file.name)
        val base = if (nes) NES_BASE else BASE
        val knownName = if (nes) null else runCatching {
            RomDatabase.lookup(context, file)?.title }.getOrNull()
        val headerTitle = runCatching { RomSystem.details(file).title }.getOrNull()
        // Existing covers are always visible offline, regardless of the
        // optional remote search permission.
        val filename = file.nameWithoutExtension
        val aliases = listOfNotNull(knownName, headerTitle, filename,
            filename.substringBefore('(').substringBefore('[').takeIf { it.length > 5 })
            .distinct().filter { it.length in 5..150 }
        val cache = File(context.filesDir, "covers").apply { mkdirs() }
        val cacheKey = if (nes) "nes_" + filename else filename
        // One physical image for multiple region/revision-tagged ROM names.
        // This key is never used for multiplayer/game identity.
        val sharedKey = BoxArtIdentity.sharedKey(file.name)

        // Reuse both the old title-keyed cache and the new filename-keyed cache.
        val keys = (listOfNotNull(sharedKey, cacheKey) + if (nes) {
            aliases.map { "nes_" + it }
        } else aliases).distinct()
        keys.forEach { alias ->
            val destination = File(cache, hash(alias) + ".png")
            if (destination.isFile && destination.length() in 1L..MAX_IMAGE.toLong()) {
                val image = runCatching { decodeSmall(destination.readBytes()) }.getOrNull()
                if (image != null) return image
                // Corrupt/incomplete thumbnails should not poison the cache.
                runCatching { destination.delete() }
            }
        }

        if (!allowNetwork) return null
        val catalog = readCatalog(cache, base, nes)
        val exactName = CoverTitleMatcher.bestCover(aliases, catalog)
        val candidates = listOfNotNull(exactName) +
            CoverTitleMatcher.directCandidates(aliases).map { "$it.png" }
        for (candidate in candidates.distinct().take(6)) {
            val safe = candidate.removeSuffix(".png")
                .replace(Regex("""[&*/:"<>?\\|]"""), "_")
            val url = base + Uri.encode(safe) + ".png"
            val data = runCatching { fetchPng(url, base) }.getOrNull() ?: continue
            val bitmap = decodeSmall(data) ?: continue
            runCatching {
                val imageKey = sharedKey ?: cacheKey
                val destination = File(cache, hash(imageKey) + ".png")
                val part = File(cache, hash(imageKey) + ".part")
                part.writeBytes(data)
                if (!part.renameTo(destination)) {
                    part.delete()
                }
            }
            return bitmap
        }
        return null
    }

    /** Fast local existence probe; never opens a socket or decodes pixels. */
    fun hasCached(context: Context, rom: File): Boolean {
        if (customFile(context, rom).isFile) return true
        val prefix = if (RomSystem.isNes(rom.name)) "nes_" else ""
        val cached = File(context.filesDir, "covers")
        val keys = listOfNotNull(
            BoxArtIdentity.sharedKey(rom.name), prefix + rom.nameWithoutExtension)
        return keys.any { key ->
            File(cached, hash(key) + ".png").length() in 1L..MAX_IMAGE.toLong()
        }
    }

    private fun customFile(context: Context, rom: File): File =
        File(File(context.filesDir, "covers").apply { mkdirs() },
            "custom_" + hash((if (RomSystem.isNes(rom.name)) "nes_" else "") +
                rom.nameWithoutExtension) + ".png")

    /**
     * Copy a selected image (PNG or JPEG) to the app's private cache.
     * Never redistribute the user's image or include it in the APK.
     */
    fun saveCustom(context: Context, rom: File, original: ByteArray): Bitmap? {
        if (original.size !in 16..MAX_IMAGE) return null
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, probe)
        if (probe.outWidth !in 16..8192 || probe.outHeight !in 16..8192) return null
        var sample = 1
        while (probe.outWidth / sample > 450 ||
            probe.outHeight / sample > 600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val result = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 88, result)) return null
        val bytes = result.toByteArray()
        if (bytes.size > MAX_IMAGE || decodeSmall(bytes) == null) return null
        val target = customFile(context, rom)
        val temp = File(target.parentFile, target.name + ".tmp")
        return try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.delete()
                null
            } else decodeSmall(bytes)
        } finally { if (temp.exists()) temp.delete() }
    }

    fun resetCustom(context: Context, rom: File): Boolean =
        customFile(context, rom).let { !it.exists() || it.delete() }

    fun moveCustom(context: Context, before: File, after: File) {
        val prior = customFile(context, before)
        if (prior.exists()) runCatching { prior.renameTo(customFile(context, after)) }
    }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun readCatalog(cache: File, base: String, nes: Boolean): List<String> {
        val index = File(cache, if (nes) "libretro_nes_boxarts.txt"
            else "libretro_snes_boxarts.txt")
        val now = System.currentTimeMillis()
        if (index.isFile && index.length() in 1L..CATALOG_MAX_BYTES.toLong() &&
            now - index.lastModified() < CATALOG_TTL_MS) {
            return runCatching { index.readLines() }.getOrDefault(emptyList())
        }
        val connection = (URL(base).openConnection() as HttpURLConnection).apply {
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

    private fun fetchPng(url: String, base: String): ByteArray {
        require(url.startsWith(base)) { "Origem de capa inválida." }
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
