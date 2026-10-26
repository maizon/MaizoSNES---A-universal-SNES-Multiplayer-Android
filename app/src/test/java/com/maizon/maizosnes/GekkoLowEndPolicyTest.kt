package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class GekkoLowEndPolicyTest {
    @Test fun disabledNeverChangesExistingNetworkChoices() {
        assertEquals(NetcodeMode.LOCKSTEP,
            GekkoLowEndPolicy.effectiveNetcode(NetcodeMode.LOCKSTEP, false))
        assertEquals(NetcodeMode.ROLLBACK,
            GekkoLowEndPolicy.effectiveNetcode(NetcodeMode.LOCKSTEP, true))
        for (mode in NetplayLatencyMode.values()) {
            assertEquals(
                when(mode) {
                    NetplayLatencyMode.RESPONSIVE -> 0
                    NetplayLatencyMode.BALANCED -> 1
                    NetplayLatencyMode.STABLE -> 3
                }, GekkoLowEndPolicy.initialLocalDelay(mode, false))
        }
        assertFalse(GekkoLowEndPolicy.preferLimitedSaving(7.0, false))
        assertTrue(GekkoLowEndPolicy.preferLimitedSaving(7.0, true))
    }

    @Test fun lowendAcceptsExpensiveButStableStateWithoutBreakingLegacyThreshold() {
        assertFalse(GekkoLowEndPolicy.readyForUdp(true, true, 1000, 27.0,
            true, false, false))
        assertTrue(GekkoLowEndPolicy.readyForUdp(true, true, 1000, 25.0,
            true, false, true))
        assertFalse(GekkoLowEndPolicy.readyForUdp(true, true, 1000, 27.0,
            true, false, true))
        assertFalse(GekkoLowEndPolicy.readyForUdp(true, false, 1000, 2.0,
            true, false, true))
        assertFalse(GekkoLowEndPolicy.readyForUdp(true, true, 1000,
            Double.NaN, true, false, true))
        assertTrue(GekkoLowEndPolicy.message(false).contains("desligado"))
    }
}
