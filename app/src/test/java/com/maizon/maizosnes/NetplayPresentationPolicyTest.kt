package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Test

class NetplayPresentationPolicyTest {
    @Test fun requiresSustainedBadRttThenSustainedRecovery() {
        val policy = NetplayPresentationPolicy()
        assertEquals(1, policy.offer(180.0))
        assertEquals(1, policy.offer(170.0))
        assertEquals(2, policy.offer(190.0))
        for (i in 1..4) assertEquals(2, policy.offer(45.0))
        assertEquals(1, policy.offer(45.0))
    }

    @Test fun invalidNetworkSamplesNeverEnableFrameSkipping() {
        val policy = NetplayPresentationPolicy()
        assertEquals(1, policy.offer(Double.NaN))
        assertEquals(1, policy.offer(-1.0))
        assertEquals(1, policy.offer(0.0))
    }
}
