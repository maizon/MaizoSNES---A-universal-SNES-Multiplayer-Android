package com.maizon.maizosnes

import java.util.Locale

/** UI-only rules: no network, ROM mutation or emulation state. */
internal object ConsoleExperienceRules {
    fun featuredGame(current: String?, recent: List<String>, games: List<String>): String? {
        val available = games.toHashSet()
        return current?.takeIf { it in available }
            ?: recent.firstOrNull { it in available }
            ?: games.firstOrNull()
    }

    fun avatarInitials(owner: String): String {
        val words = owner.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "P1"
        return (if (words.size == 1) words[0].take(2)
            else words.take(2).joinToString("") { it.take(1) })
            .uppercase(Locale.ROOT)
    }
}
