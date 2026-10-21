package com.maizon.maizosnes

import java.security.SecureRandom

/** A local-only profile; its identifier is not an account or authentication token. */
internal data class PlayerProfile(
    val name: String = "Jogador",
    val localId: String = "",
    val sharePhotoDirect: Boolean = false
)
internal object PlayerIdentity {
    private val disallowed = Regex("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]")
    private val spaces = Regex("\\s+")
    fun sanitize(value: String): String =
        spaces.replace(disallowed.replace(value, " "), " ").trim().take(32)
    fun defaultName(): String = "Jogador" + SecureRandom().nextInt(10000).toString().padStart(4, '0')
    fun initials(name: String): String {
        val words = sanitize(name).split(' ').filter(String::isNotBlank)
        return when (words.size) {
            0 -> "JG"
            1 -> words[0].take(2).uppercase()
            else -> (words.first().take(1) + words.last().take(1)).uppercase()
        }
    }
    fun colorIndex(localId: String, name: String): Int =
        ((localId.ifBlank { name }).hashCode().toLong() and 0x7fffffffL).rem(12).toInt()
}
