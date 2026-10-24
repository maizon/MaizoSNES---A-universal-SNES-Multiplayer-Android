package com.maizon.maizosnes

import org.junit.Test
import org.junit.Assert.*

class RomSystemTest {
    @Test fun snesStillAcceptedAndNesUsesSeparateCore() {
        assertTrue(RomSystem.supported("zelda.sfc"))
        assertTrue(RomSystem.supported("zelda.smc"))
        assertFalse(RomSystem.isNes("zelda.sfc"))
        for (extension in listOf("nes", "unf", "unif", "fds")) {
            assertTrue(RomSystem.supported("game.$extension"))
            assertTrue(RomSystem.isNes("game.$extension"))
        }
        assertTrue(RomSystem.isNes("MARIO.NES"))
        assertFalse(RomSystem.supported("video.mp4"))
        assertEquals("Nintendo NES", RomSystem.platform("game.nes"))
        assertEquals("Super Nintendo", RomSystem.platform("game.sfc"))
    }
}
