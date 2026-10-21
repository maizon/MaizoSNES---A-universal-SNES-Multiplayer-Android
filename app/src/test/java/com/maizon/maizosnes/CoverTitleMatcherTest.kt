package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTitleMatcherTest {
    private val directory = listOf(
        "Donkey Kong Country (USA).png",
        "Donkey Kong Country 3 - Dixie Kong's Double Trouble! (Europe) (En,Fr,De).png",
        "Donkey Kong Country 3 - Dixie Kong's Double Trouble! (USA) (En,Fr).png",
        "Super Mario World (USA).png",
        "Super Mario World 2 - Yoshi's Island (USA).png",
        "Inspector Gadget (USA).png"
    )

    @Test fun titleMatchesCatalogSubtitleWithoutHash() {
        assertEquals(directory[2], CoverTitleMatcher.bestCover(
            listOf("DONKEY KONG COUNTRY 3", "Donkey Kong Country 3 (USA)"), directory))
    }

    @Test fun regionAndTranslationTagsAreIgnored() {
        assertEquals("Super Mario World (USA).png",
            CoverTitleMatcher.bestCover(
                listOf("Super Mario World (USA) [T+Por-BR].sfc"), directory))
    }

    @Test fun doesNotAssignWrongGameToOtherTitle() {
        assertNull(CoverTitleMatcher.bestCover(listOf("Another Totally Different Game"), directory))
    }

    @Test fun htmlIndexParsingSafelyDecodesNames() {
        val html = """<a href="Donkey%20Kong%20Country%203%20-%20Dixie%20Kong%27s%20Double%20Trouble%21%20%28USA%29.png">ok</a>
<a href="not-an-image.txt">bad</a>"""
        val names = CoverTitleMatcher.namesFromIndex(html)
        assertEquals(listOf("Donkey Kong Country 3 - Dixie Kong's Double Trouble! (USA).png"), names)
        assertTrue(CoverTitleMatcher.directCandidates(listOf("Game (USA)"))
            .contains("Game (USA)"))
    }
}
