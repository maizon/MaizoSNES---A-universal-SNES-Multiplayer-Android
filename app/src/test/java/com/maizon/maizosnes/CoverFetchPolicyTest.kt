package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFetchPolicyTest {
    @Test fun networkRequiresExplicitConsentAndNoGameplay() {
        assertFalse(CoverFetchPolicy.networkAllowed(false, false, false))
        assertTrue(CoverFetchPolicy.networkAllowed(true, false, false))
        assertFalse(CoverFetchPolicy.networkAllowed(true, true, false))
        assertFalse(CoverFetchPolicy.networkAllowed(true, false, true))
    }
    @Test fun duplicatesAreNotQueuedAndAlreadyCachedExcluded() {
        assertEquals(listOf("B.nes"), CoverFetchPolicy.pendingCoverNames(
            listOf("A.sfc", "B.nes", "A.sfc"), setOf("A.sfc")))
    }
}
