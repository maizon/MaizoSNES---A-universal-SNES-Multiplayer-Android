package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class BoxArtIdentityTest {
    @Test fun regionAndRevisionTagsReuseArtworkWithinConsole() {
        assertEquals(BoxArtIdentity.sharedKey("Super Mario Bros (USA).nes"),
            BoxArtIdentity.sharedKey("Super Mario Bros (Rev 1).nes"))
        assertEquals(BoxArtIdentity.sharedKey("Super Mario World (USA).sfc"),
            BoxArtIdentity.sharedKey("Super Mario World (Europe).smc"))
    }

    @Test fun systemsAreIsolatedAndShortNamesAreNotCollapsed() {
        assertNotEquals(BoxArtIdentity.sharedKey("Contra.nes"),
            BoxArtIdentity.sharedKey("Contra.sfc"))
        assertNull(BoxArtIdentity.sharedKey("AB.nes"))
    }
}
