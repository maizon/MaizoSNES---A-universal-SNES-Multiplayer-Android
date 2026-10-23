package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

/** JVM contract tests for discovery advertisements; no Wi-Fi hardware needed. */
class LanDiscoveryProtocolTest {
    @Test fun validOfferParsesAllFields() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER2|55435|Super Mario World|0123456789abcdef|1", "192.168.1.42")
        assertNotNull(room)
        assertEquals("192.168.1.42", room!!.host)
        assertEquals(55435, room.port)
        assertEquals("Super Mario World", room.game)
        assertEquals("0123456789abcdef", room.romHash)
        assertTrue(room.cheats)
    }

    @Test fun modernOfferExposesNameWithoutChangingOldFormat() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER3|55435|Mario Kart|0123456789abcdef|0|4d61697a6f6e|R",
            "192.168.1.9")
        assertNotNull(room)
        assertEquals("Maizon", room!!.ownerName)
        assertEquals("Rollback disponível", room.netcode)
        assertFalse(room.cheats)
        val old = LanDiscovery.parseOffer(
            "MZOFFER2|55435|Mario Kart|0123456789abcdef|1", "192.168.1.9")!!
        assertEquals("", old.ownerName)
        assertTrue(old.cheats)
    }

    @Test fun remoteControllerRoomIsVisibleWithoutRomAndDoesNotAlterGekko() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER3|55437|Super Mario World|0123456789abcdef|0|4d61697a6f6e|C",
            "192.168.1.24")
        assertNotNull(room)
        assertEquals(55437, room!!.port)
        assertEquals("Controle remoto", room.netcode)
        assertEquals("Maizon", room.ownerName)
        assertEquals("Super Mario World", room.game)
    }

    @Test fun dualModeLanRoomOffersBothButtonsAndGameTransferPath() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER3|55435|Super Bomberman|0123456789abcdef|0|4d61697a6f6e|B",
            "192.168.0.20")!!
        assertEquals(55435, room.port) // TCP game/ROM session; UDP pads use 55437
        assertEquals("Maizon", room.ownerName)
        assertEquals("Jogar ou controle", room.netcode)
        assertTrue("Game option must be available", room.acceptsGameClient)
        assertTrue("Remote controller must be available", room.acceptsRemotePad)
    }

    @Test fun oldLanRoomKeepsGameWithoutAdvertisingIncompatibleController() {
        val old = LanDiscovery.parseOffer(
            "MZOFFER3|55435|Homebrew|0123456789abcdef|0||R",
            "192.168.0.19")!!
        assertTrue(old.acceptsGameClient)
        assertFalse(old.acceptsRemotePad)
        val remoteOnly = LanDiscovery.parseOffer(
            "MZOFFER3|55437|Homebrew|0123456789abcdef|0||C",
            "192.168.0.21")!!
        assertTrue(remoteOnly.acceptsRemotePad)
        assertFalse(remoteOnly.acceptsGameClient)
    }

    @Test fun invalidOwnerMetadataIsIgnoredWithoutDiscardingGame() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER3|55435|Homebrew|0123456789abcdef|0|!!!!|L", "192.168.1.9")
        assertNotNull(room)
        assertEquals("", room!!.ownerName)
        assertEquals("Lockstep", room.netcode)
    }

    @Test fun legacyTwoFieldOfferKeepsCompatibility() {
        val room = LanDiscovery.parseOffer("MZOFFER2|55435|Homebrew", "192.168.1.3")
        assertNotNull(room)
        assertEquals("", room!!.romHash)
        assertFalse(room.cheats)
    }

    @Test fun invalidPortOrProtocolIsRejected() {
        assertNull(LanDiscovery.parseOffer("MZOFFER1|55435|Game", "192.168.1.2"))
        assertNull(LanDiscovery.parseOffer("MZOFFER2|0|Game", "192.168.1.2"))
        assertNull(LanDiscovery.parseOffer("MZOFFER2|65536|Game", "192.168.1.2"))
        assertNull(LanDiscovery.parseOffer("MZOFFER2|abc|Game", "192.168.1.2"))
        assertNull(LanDiscovery.parseOffer("MZOFFER2|55435| ", "192.168.1.2"))
    }

    @Test fun malformedHashIsDiscardedWithoutDiscardingRoom() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER2|55435|Game|not-a-hash|0", "192.168.1.2")
        assertNotNull(room)
        assertEquals("", room!!.romHash)
        assertFalse(room.cheats)
    }

    @Test fun hashIsNormalizedToLowercase() {
        val room = LanDiscovery.parseOffer(
            "MZOFFER2|55435|Game|ABCDEF0123456789|0", "192.168.1.2")
        assertEquals("abcdef0123456789", room!!.romHash)
    }
}
