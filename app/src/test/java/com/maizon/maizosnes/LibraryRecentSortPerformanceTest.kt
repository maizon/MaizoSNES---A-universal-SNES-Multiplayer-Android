package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class LibraryRecentSortPerformanceTest {
    @Test fun preservesRecentOrderingWithUnknownGames() {
        val names = listOf("z.nes", "a.sfc", "b.nes", "other.sfc")
        val recent = listOf("b.nes", "missing.sfc", "a.sfc")
        val titles = mapOf("z.nes" to "Zebra", "other.sfc" to "Alpha")
        assertEquals(listOf("b.nes", "a.sfc", "other.sfc", "z.nes"),
            LibraryUiRules.order(names, titles, recent, "Recentes"))
    }

    @Test fun handlesLargeLibraryWithStableOrder() {
        val names = (0 until 1000).map { "rom_$it.nes" }
        val result = LibraryUiRules.order(names, emptyMap(),
            listOf("rom_800.nes", "rom_19.nes"), "Recentes")
        assertEquals("rom_800.nes", result[0])
        assertEquals("rom_19.nes", result[1])
        assertEquals(1000, result.size)
    }
}
