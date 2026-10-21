package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryUiRulesTest {
    @Test fun responsiveColumnsFitPhonesTabletsAndLandscape() {
        assertEquals(2, LibraryUiRules.columns(360f, 0))
        assertEquals(3, LibraryUiRules.columns(500f, 0))
        assertEquals(4, LibraryUiRules.columns(760f, 0))
        assertEquals(5, LibraryUiRules.columns(1024f, 0))
        assertEquals(2, LibraryUiRules.columns(1024f, 2))
        assertEquals(4, LibraryUiRules.columns(360f, 4))
    }

    @Test fun recentSortNeverPromotesUnknownGames() {
        val names = listOf("Metroid.sfc", "Donkey.sfc", "Zelda.sfc")
        val recent = listOf("Zelda.sfc", "Donkey.sfc")
        assertEquals(listOf("Zelda.sfc", "Donkey.sfc", "Metroid.sfc"),
            LibraryUiRules.order(names, emptyMap(), recent, "Recentes"))
    }

    @Test fun titleSortUsesMetadataInsteadOfOnlyFilename() {
        val names = listOf("ZZZ.sfc", "AAA.sfc")
        assertEquals(listOf("ZZZ.sfc", "AAA.sfc"),
            LibraryUiRules.order(names,
                mapOf("ZZZ.sfc" to "Donkey Kong", "AAA.sfc" to "Zelda"),
                emptyList(), "Título"))
    }
}
