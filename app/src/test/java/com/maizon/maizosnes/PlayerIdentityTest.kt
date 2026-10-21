package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class PlayerIdentityTest {
    @Test fun userNameIsSanitizedAndBounded() {
        assertEquals("Maizon Cool", PlayerIdentity.sanitize("  Maizon\n\t Cool  "))
        assertEquals(32, PlayerIdentity.sanitize("x".repeat(100)).length)
        assertEquals("", PlayerIdentity.sanitize("\u0000\u0001\n"))
    }
    @Test fun defaultIsOptionalAndStableAvatarColorUsesLocalId() {
        val fallback = PlayerIdentity.defaultName()
        assertTrue(fallback.matches(Regex("Jogador[0-9]{4}")))
        assertEquals("MN", PlayerIdentity.initials("Maicon Nascimento"))
        assertEquals("MA", PlayerIdentity.initials("Maizon"))
        val color = PlayerIdentity.colorIndex("stable-id", "Maizon")
        assertTrue(color in 0..11)
        assertEquals(color, PlayerIdentity.colorIndex("stable-id", "Other"))
    }
}
