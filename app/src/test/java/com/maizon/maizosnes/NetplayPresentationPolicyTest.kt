package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Test

class NetplayPresentationPolicyTest {
    @Test fun highRttWithoutLocalOverloadNeverSkipsVideo() {
        val policy = NetplayPresentationPolicy()
        repeat(10) { assertEquals(1, policy.offer(290.0, 4.0, 16.67)) }
    }
    @Test fun repeatedHeavyTicksCanReducePresentationAndRecover() {
        val policy = NetplayPresentationPolicy()
        assertEquals(1, policy.offer(35.0, 16.1, 16.67))
        assertEquals(1, policy.offer(35.0, 16.1, 16.67))
        assertEquals(2, policy.offer(35.0, 16.1, 16.67))
        repeat(4) { assertEquals(2, policy.offer(220.0, 5.0, 16.67)) }
        assertEquals(1, policy.offer(220.0, 5.0, 16.67))
    }
    @Test fun noMeasurementNeverEnablesFrameSkipping() {
        val policy = NetplayPresentationPolicy()
        repeat(12) { assertEquals(1, policy.offer(Double.NaN)) }
        assertEquals(1, policy.offer(-1.0, Double.NaN, 16.67))
    }
}
