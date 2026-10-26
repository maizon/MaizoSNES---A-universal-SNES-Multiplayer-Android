package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class GekkoLowEndAdaptationTest {
    @Test fun lowendStartsWithTwoFramesAndRespondsToFewerRollbacks() {
        val regular = RollbackDelayController(NetplayLatencyMode.BALANCED)
        val lowend = RollbackDelayController(NetplayLatencyMode.BALANCED, true)
        assertEquals(1, regular.delay)
        assertEquals(2, lowend.delay)
        regular.offer(1L, 0L, 40.0, 10.0)
        lowend.offer(1L, 0L, 40.0, 10.0)
        val after = 5_000_000_001L
        assertNull(regular.offer(after, 2L, 50.0, 4.0))
        assertEquals(3, lowend.offer(after, 2L, 50.0, 4.0))
        assertEquals(1, regular.delay)
        assertEquals(3, lowend.delay)
    }

    @Test fun lowendPresentationOnlySkipsWhenCpuActuallyOverloaded() {
        val standard = NetplayPresentationPolicy()
        val lowend = NetplayPresentationPolicy(true)
        assertEquals(1, lowend.offer(400.0, 3.0, 16.67))
        assertEquals(1, standard.offer(10.0, 14.0, 16.67))
        assertEquals(1, lowend.offer(10.0, 14.0, 16.67))
        assertEquals(2, lowend.offer(10.0, 14.0, 16.67))
        assertEquals(1, standard.offer(10.0, 14.0, 16.67))
        repeat(6) { lowend.offer(300.0, 4.0, 16.67) }
        assertEquals(1, lowend.offer(300.0, 4.0, 16.67))
    }
}
