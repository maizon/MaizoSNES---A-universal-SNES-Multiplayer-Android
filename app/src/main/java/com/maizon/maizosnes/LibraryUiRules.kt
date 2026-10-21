package com.maizon.maizosnes

/** Stable viewport breakpoints for a cover-first library on phones, tablets and TV. */
internal object LibraryUiRules {
    fun autoColumns(widthDp: Float): Int = when {
        widthDp >= 980f -> 5
        widthDp >= 690f -> 4
        widthDp >= 490f -> 3
        else -> 2
    }

    fun columns(widthDp: Float, override: Int): Int =
        if (override in 2..5) override else autoColumns(widthDp)

    fun order(names: List<String>, titles: Map<String, String>,
              recent: List<String>, mode: String): List<String> = when (mode) {
        "Recentes" -> names.sortedWith(compareBy<String> {
            recent.indexOf(it).let { pos -> if (pos < 0) Int.MAX_VALUE else pos }
        }.thenBy { titles[it]?.lowercase() ?: it.lowercase() })
        "Arquivo" -> names.sortedBy { it.lowercase() }
        else -> names.sortedBy { (titles[it] ?: it).lowercase() }
    }
}
