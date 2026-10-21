package com.maizon.maizosnes

import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/**
 * No network or Android dependencies: deterministic, conservative matching
 * against Libretro's publicly indexed SNES boxart filenames.
 * Never uses partial hashes to identify images.
 */
internal object CoverTitleMatcher {
    private val tags = Regex("""\s*\([^)]*\)|\s*\[[^]]*\]""")
    private val punctuation = Regex("""[^a-z0-9]+""")
    private val space = Regex("""\s+""")
    private val region = Regex("""\((?:USA|Europe|World|Japan|Brazil|USA, Europe)[^)]*\)""", RegexOption.IGNORE_CASE)
    private val badEdition = Regex("""\((?:Beta|Proto|Sample|Demo|Hack|Unl)[^)]*\)""", RegexOption.IGNORE_CASE)

    fun normalize(title: String): String {
        val noExtension = title.replace(Regex("""\.(smc|sfc|fig|zip|png)$""", RegexOption.IGNORE_CASE), "")
        val noTags = noExtension.replace(tags, " ").replace('_', ' ')
        val latin = Normalizer.normalize(noTags, Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .lowercase(Locale.ROOT)
        return latin.replace(punctuation, " ").replace(space, " ").trim()
    }

    /** HTML index from thumbnails.libretro.com, not remote script/content. */
    fun namesFromIndex(html: String): List<String> =
        Regex("""href="([^"]+\.png)""", RegexOption.IGNORE_CASE)
            .findAll(html).mapNotNull { match ->
                val raw = match.groupValues[1].substringAfterLast('/')
                val decoded = runCatching {
                    URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
                        .replace("&amp;", "&")
                }.getOrNull() ?: return@mapNotNull null
                decoded.takeIf { it.length in 5..220 &&
                    it.endsWith(".png", ignoreCase = true) &&
                    !it.contains('/') && !it.contains('\\') }
            }.distinct().toList()

    /** Exact normalized title preferred, then an unambiguous longer subtitle. */
    fun bestCover(aliases: List<String>, covers: List<String>): String? {
        val wanted = aliases.map(::normalize).filter { it.length >= 6 }.distinct()
        if (wanted.isEmpty()) return null
        var best: String? = null
        var bestScore = 0
        for (name in covers) {
            if (!name.endsWith(".png", ignoreCase = true)) continue
            val title = normalize(name)
            var score = 0
            for (target in wanted) {
                val value = when {
                    title == target -> 120
                    target.length >= 12 && title.startsWith("$target ") &&
                        name.contains(" - ") -> 95
                    title.length >= 12 && target.startsWith("$title ") &&
                        target.length - title.length <= 8 -> 87
                    else -> 0
                }
                score = maxOf(score, value)
            }
            if (score == 0) continue
            if (region.containsMatchIn(name)) score +=
                if (name.contains("(USA)", true)) 8 else
                if (name.contains("(Europe)", true)) 6 else 2
            if (badEdition.containsMatchIn(name)) score -= 25
            if (score > bestScore || score == bestScore &&
                best != null && name.length < best.length) {
                bestScore = score
                best = name
            }
        }
        return best
    }

    /** Simple local guesses when server index is unreachable. */
    fun directCandidates(aliases: List<String>): List<String> =
        aliases.flatMap { alias ->
            val stripped = alias.substringBefore('(').substringBefore('[')
                .replace('_', ' ').trim().take(100)
            listOf(alias, "$stripped (USA)", "$stripped (Europe)", stripped)
        }.map { it.replace(Regex("""[&*/:"<>?\\|]"""), "_").trim() }
            .filter { it.length >= 5 }.distinct().take(12)
}
