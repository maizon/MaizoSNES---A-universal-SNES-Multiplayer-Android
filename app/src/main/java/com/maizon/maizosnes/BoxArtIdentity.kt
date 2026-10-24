package com.maizon.maizosnes

/**
 * Safe equivalence key for sharing a downloaded box-art asset between
 * differently tagged dumps of the same game, scoped to the console.
 * This is deliberately ONLY for cover artwork, never ROM/network identity.
 */
internal object BoxArtIdentity {
    fun sharedKey(name: String): String? {
        val title = name.substringBeforeLast('.', name)
        val normalized = CoverTitleMatcher.normalize(title)
        if (normalized.length < 6) return null
        val system = if (RomSystem.isNes(name)) "nes" else "snes"
        return "boxart_${system}_$normalized"
    }
}
