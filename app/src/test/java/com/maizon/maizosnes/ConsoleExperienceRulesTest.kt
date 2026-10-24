package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleExperienceRulesTest {
    @Test fun featuredGamePreservesCurrentThenRecent() {
        val games = listOf("A.sfc", "B.sfc")
        assertEquals("A.sfc", ConsoleExperienceRules.featuredGame("A.sfc", listOf("B.sfc"), games))
        assertEquals("B.sfc", ConsoleExperienceRules.featuredGame("missing", listOf("B.sfc"), games))
        assertEquals("A.sfc", ConsoleExperienceRules.featuredGame(null, emptyList(), games))
        assertEquals(null, ConsoleExperienceRules.featuredGame(null, emptyList(), emptyList()))
    }

    @Test fun avatarInitialsStayLocal() {
        assertEquals("P1", ConsoleExperienceRules.avatarInitials(" "))
        assertEquals("MA", ConsoleExperienceRules.avatarInitials("Maizon"))
        assertEquals("MN", ConsoleExperienceRules.avatarInitials("Maicon Nascimento"))
        assertEquals("AB", ConsoleExperienceRules.avatarInitials("Ana   Beatriz"))
    }
}
