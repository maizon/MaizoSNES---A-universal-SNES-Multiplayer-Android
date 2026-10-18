package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure parsing tests: no Android device, commercial game or network needed. */
class CheatAndDiscoveryTest {
    @Test fun cheatParserLoadsNamesAndSafeCodes() {
        val sample = """
            cheats = 3
            cheat0_desc = "Infinite Lives"
            cheat0_code = "7E0DBE63"
            cheat0_enable = false
            cheat1_desc = "Invincibility"
            cheat1_code = "DDB4-6F07+7E001900"
            cheat2_desc = "Not safe"
            cheat2_code = "BAD;$(1)"
        """.trimIndent()
        val cheats = CheatCatalog.parse("Synthetic (USA).cht", sample)
        assertEquals(2, cheats.size)
        assertEquals("Infinite Lives", cheats[0].title)
        assertEquals("7E0DBE63", cheats[0].code)
        assertEquals("DDB4-6F07+7E001900", cheats[1].code)
        assertTrue(cheats[0].id.startsWith("Synthetic (USA).cht#"))
    }

    @Test fun networkOfferParserRejectsUnrelatedAndInvalidMessages() {
        assertNull(LanDiscovery.parseOffer("MZSCAN2", "192.168.1.10"))
        assertNull(LanDiscovery.parseOffer("MZOFFER2|12|Game", "192.168.1.10"))
        assertNull(LanDiscovery.parseOffer("MZOFFER2|55435|", "192.168.1.10"))
        val room = LanDiscovery.parseOffer("MZOFFER2|55435|Test Homebrew", "192.168.1.10")
        assertEquals("192.168.1.10", room?.host)
        assertEquals(55435, room?.port)
        assertEquals("Test Homebrew", room?.game)
        assertEquals("", room?.romHash)
        assertFalse(room?.cheats ?: true)
        val newer = LanDiscovery.parseOffer(
            "MZOFFER2|55435|Test Homebrew|0123456789abcdef|1", "192.168.1.10")
        assertEquals("0123456789abcdef", newer?.romHash)
        assertTrue(newer?.cheats ?: false)
        val incompatible = LanDiscovery.parseOffer(
            "MZOFFER2|55435|Test Homebrew|not-a-checksum|0", "192.168.1.10")
        assertEquals("", incompatible?.romHash)
    }
}
