package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RollbackDelayControllerTest {
    @Test fun mobileJitterBumpsDelayButNeverBeyondProfileCap() {
        val controller = RollbackDelayController(NetplayLatencyMode.BALANCED)
        assertEquals(1, controller.delay)
        assertNull(controller.offer(1L, 0, 20.0, 3.0))
        for (i in 1..12) controller.offer(1L + i * 5_000_000_000L,
            i * 10L, 220.0, 30.0)
        assertEquals(5, controller.delay)
    }

    @Test fun calmNetworkDecreasesDelayOnlyAfterLongStableWindow() {
        val controller = RollbackDelayController(NetplayLatencyMode.STABLE)
        controller.offer(1L, 0L, 2.0, 0.1)
        controller.offer(5_000_000_001L, 8L, 200.0, 31.0)
        assertEquals(4, controller.delay)
        for (i in 2..5) controller.offer(1L + i * 5_000_000_000L,
            8L, 2.0, 0.1)
        assertEquals(4, controller.delay)
        controller.offer(30_000_000_001L, 8L, 2.0, 0.1)
        assertEquals(3, controller.delay)
    }

    @Test fun noChangesOnSubFiveSecondNetworkFluctuations() {
        val controller = RollbackDelayController(NetplayLatencyMode.RESPONSIVE)
        controller.offer(1L, 0L, 20.0, 0.0)
        assertNull(controller.offer(4000000000L, 300L, 300.0, 100.0))
        assertEquals(0, controller.delay)
    }
}
