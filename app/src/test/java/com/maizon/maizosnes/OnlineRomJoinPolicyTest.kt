package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class OnlineRomJoinPolicyTest {
    @Test fun sameRomRemainsPlayableOnLegacyRelay() {
        assertTrue(OnlineRomJoinPolicy.canJoin(true, true, false, false))
    }
    @Test fun mismatchedOrMissingRomRequiresTlsAndReceiving() {
        assertFalse(OnlineRomJoinPolicy.canJoin(false, true, false, true))
        assertFalse(OnlineRomJoinPolicy.canJoin(false, true, true, false))
        assertFalse(OnlineRomJoinPolicy.canJoin(true, false, true, false))
        assertFalse(OnlineRomJoinPolicy.canJoin(true, false, false, true))
        assertTrue(OnlineRomJoinPolicy.canJoin(false, false, true, true))
        assertTrue(OnlineRomJoinPolicy.canJoin(true, false, true, true))
    }
}
