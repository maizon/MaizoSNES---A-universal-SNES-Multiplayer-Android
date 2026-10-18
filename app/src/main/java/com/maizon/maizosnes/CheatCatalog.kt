package com.maizon.maizosnes

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class CheatEntry(
    val id: String,
    val title: String,
    val code: String,
    val source: String
)
internal data class CheatSearchResult(val entries: List<CheatEntry>, val message: String)

/**
 * On-demand search in a pinned, openly licensed CC BY-SA 4.0 libretro-database
 * snapshot. No cheats or commercial ROMs are bundled in this APK.
 *
 * Matching is by normalized filename (NOT a cryptographic game revision match).
 * Cheats for different regions and ROM hacks may be invalid; the UI warns users.
 * All network I/O must run on Dispatchers.IO, never the Compose/UI thread.
 */
internal object CheatCatalog {
    private const val REVISION = "12c21cf79f1e20eff6ac68b80875816c717d0a7a"
    private const val TREE_SHA = "6b1f8a463a28d8c3450901844c13112d469e22be"
    private const val TREE_URL =
        "https://api.github.com/repos/libretro/libretro-database/git/trees/$TREE_SHA"
    private const val RAW_BASE =
        "https://raw.githubusercontent.com/libretro/libretro-database/$REVISION/cht/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/"
    private const val MAX_INDEX = 1_600_000
    private const val MAX_CHEAT_FILE = 500_000
    private val legalCode = Regex("^[0-9a-fA-F+,:;. \\-]{4,190}$")
    private val removeTags = Regex("\\s*\\([^)]*\\)")
    private val removeBrackets = Regex("\\s*\\[[^]]*\\]")
    private val nonAlnum = Regex("[^a-z0-9]")
    private val excludeVariants = Regex("(?i)\\b(beta|proto|prototype|demo|rumble|translation|hack)\\b")

    fun find(context: Context, romFile: File, refresh: Boolean = false): CheatSearchResult {
        val name = romFile.nameWithoutExtension
        val verified = runCatching { RomDatabase.lookup(context, romFile, refresh) }.getOrNull()
        val headerTitle = readSnesHeaderTitle(romFile)
        val keys = (if (verified != null) listOf(normalize(verified.title))
            else listOfNotNull(normalize(name), headerTitle?.let(::normalize)))
            .filter { it.length >= 4 }.toSet()
        if (keys.isEmpty()) return CheatSearchResult(emptyList(),
            "ROM sem nome ou título interno suficiente para identificar cheats.")
        val cache = File(context.filesDir, "cheat_catalog").apply { mkdirs() }
        val index = loadCache(cache, "tree-$TREE_SHA.json", TREE_URL, MAX_INDEX, refresh)
        val tree = JSONObject(index).getJSONArray("tree")
        val candidates = mutableListOf<String>()
        for (i in 0 until tree.length()) {
            val entry = tree.getJSONObject(i)
            if (entry.optString("type") != "blob") continue
            val candidate = entry.optString("path")
            if (!candidate.endsWith(".cht", ignoreCase = true)) continue
            if (normalize(candidate.removeSuffix(".cht")) !in keys) continue
            if (excludeVariants.containsMatchIn(candidate) &&
                !excludeVariants.containsMatchIn(verified?.title ?: name)) continue
            if (verified?.region != null) {
                val regionTag = Regex("\\((USA|Europe|Japan|World|Brazil)(?:, [^)]*)?\\)",
                    RegexOption.IGNORE_CASE).find(candidate)?.groupValues?.get(1)
                if (regionTag != null && !regionTag.equals("World", true) &&
                    !candidate.contains(verified.region, ignoreCase = true)) continue
            }
            candidates.add(candidate)
        }
        if (candidates.isEmpty()) return CheatSearchResult(emptyList(),
            "Nenhum cheat encontrado para '$name'. A busca usa o nome da ROM; renomeações e hacks podem dificultar a identificação.")
        val region = verified?.region?.lowercase() ?: Regex("\\((USA|Europe|Japan|World|Brazil)\\)",
            RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.lowercase()
        val ranked = candidates.sortedWith(
            compareByDescending<String> { file ->
                var score = 0
                if (file.removeSuffix(".cht").equals(name, ignoreCase = true)) score += 100
                if (region != null && file.contains("($region)", ignoreCase = true)) score += 40
                if (!file.contains("Action Replay", true) && !file.contains("Game Genie", true)) score += 20
                score
            }.thenBy { it.length }
        ).take(4)

        val result = mutableListOf<CheatEntry>()
        val failures = mutableListOf<String>()
        for (candidate in ranked) {
            try {
                val content = loadCache(cache, cacheKey(candidate), RAW_BASE + Uri.encode(candidate),
                    MAX_CHEAT_FILE, refresh)
                result += parse(candidate, content)
            } catch (error: Exception) {
                failures += (error.message ?: "falha ao baixar $candidate").take(90)
            }
        }
        val limited = result.take(1200)
        val message = when {
            limited.isNotEmpty() -> {
                val confidence = if (verified != null)
                    "CRC32 + tamanho + SHA-1 confirmados: ${verified.title}. " +
                        "Cheats ainda exigem teste por versão."
                else "Sem checksum identificado: resultados por nome/título, não verificados."
                "${limited.size} cheats encontrados. $confidence"
            }
            failures.isNotEmpty() -> "Falha ao baixar códigos: ${failures.first()}"
            else -> "Foram encontrados arquivos de cheats, mas não havia códigos compatíveis."
        }
        return CheatSearchResult(limited, message)
    }

    /**
     * SNES stores a short ASCII title in its LoROM/HiROM header. This fallback
     * allows matching renamed .sfc files without asking a player to type cheats.
     * It is heuristic and must never be treated as a ROM integrity check.
     */
    private fun readSnesHeaderTitle(file: File): String? = runCatching {
        val length = file.length()
        if (length < 32768L || length > 64L * 1024 * 1024) return@runCatching null
        val copierHeader = if (length % 1024L == 512L) 512L else 0L
        java.io.RandomAccessFile(file, "r").use { stream ->
            for (location in listOf(0x7FC0L, 0xFFC0L, 0x40FFC0L)) {
                val offset = location + copierHeader
                if (offset + 21 > length) continue
                stream.seek(offset)
                val bytes = ByteArray(21)
                stream.readFully(bytes)
                if (bytes.count { it.toInt() in 32..126 } < 18) continue
                val title = bytes.toString(Charsets.US_ASCII).trim(' ', '\u0000')
                if (title.length >= 4 && title.any { it.isLetter() }) {
                    return@runCatching title
                }
            }
        }
        null
    }.getOrNull()

    private fun normalize(value: String): String =
        nonAlnum.replace(
            removeBrackets.replace(
                removeTags.replace(value.substringBeforeLast(".cht").substringBeforeLast(".sfc")
                    .substringBeforeLast(".smc"), ""), ""
            ).lowercase(), ""
        )

    private fun cacheKey(filename: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(filename.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "cheats-$hash.txt"
    }

    private fun loadCache(
        directory: File, cacheName: String, url: String, limit: Int, refresh: Boolean
    ): String {
        val destination = File(directory, cacheName)
        if (!refresh && destination.isFile &&
            destination.length() in 1L..limit.toLong()) {
            return destination.readText(Charsets.UTF_8)
        }
        val remote = readHttps(url, limit)
        val temporary = File(directory, cacheName + ".part")
        temporary.writeText(remote, Charsets.UTF_8)
        if (!temporary.renameTo(destination)) {
            // Replacing an existing cache entry is allowed only within app-private storage.
            destination.delete()
            check(temporary.renameTo(destination)) { "Falha ao gravar cache de cheats." }
        }
        return remote
    }

    private fun readHttps(url: String, limit: Int): String {
        require(url.startsWith(TREE_URL) || url.startsWith(RAW_BASE)) {
            "Origem da base de cheats não autorizada."
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "MaizoSNES-Android-CheatCatalog")
            setRequestProperty("Accept", "application/json, text/plain")
            instanceFollowRedirects = false
        }
        try {
            require(connection.responseCode == 200) {
                "Serviço de cheats indisponível (HTTP ${connection.responseCode})."
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16384)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= limit) { "Catálogo remoto excede limite." }
                    output.write(buffer, 0, n)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(source: String, input: String): List<CheatEntry> {
        val descriptions = hashMapOf<Int, String>()
        val codes = hashMapOf<Int, String>()
        for (line in input.lineSequence()) {
            val pos = line.indexOf('=')
            if (pos <= 0) continue
            val field = line.substring(0, pos).trim()
            if (!field.startsWith("cheat")) continue
            val suffix = field.substring(5)
            val type = when {
                suffix.endsWith("_desc") -> "_desc"
                suffix.endsWith("_code") -> "_code"
                else -> continue
            }
            val index = suffix.removeSuffix(type).toIntOrNull() ?: continue
            if (index !in 0..5000) continue
            val value = line.substring(pos + 1).trim().removeSurrounding("\"").trim()
            if (type == "_desc") descriptions[index] = value.take(180)
            else codes[index] = value
        }
        return codes.toSortedMap().mapNotNull { (index, code) ->
            if (!legalCode.matches(code) || descriptions[index].isNullOrBlank()) null
            else CheatEntry("$source#$index", descriptions.getValue(index),
                code.uppercase(), source.removeSuffix(".cht"))
        }
    }
}
