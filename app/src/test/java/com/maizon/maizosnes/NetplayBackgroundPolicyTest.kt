package com.maizon.maizosnes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayBackgroundPolicyTest {
    @Test fun movingBetweenApplicationsNeverPausesAnActiveMultiplayerSession() {
        assertFalse(NetplayBackgroundPolicy.pauseCore(true, false))
        assertFalse(NetplayBackgroundPolicy.pauseCore(true, true))
    }

    @Test fun offlineGameplayPausesOnAppSwitchAndResumesOnReturn() {
        assertTrue(NetplayBackgroundPolicy.pauseCore(false, false))
        assertFalse(NetplayBackgroundPolicy.pauseCore(false, true))
    }

    @Test fun optionalForegroundProtectionDefaultsToOnButCanBeDisabled() {
        assertTrue(NetplayBackgroundPolicy.needsForegroundService(true, true))
        assertFalse(NetplayBackgroundPolicy.needsForegroundService(true, false))
        assertFalse(NetplayBackgroundPolicy.needsForegroundService(false, true))
    }
}
