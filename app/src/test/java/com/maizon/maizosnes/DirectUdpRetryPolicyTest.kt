package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectUdpRetryPolicyTest {
    @Test fun threeAttemptsUseFreshPortsWithoutClashingWithDiscovery() {
        val asked = mutableListOf<Int>()
        val returned = listOf(55436, 50008, 50009)
        val results = (1..DirectUdpRetryPolicy.ATTEMPTS).map { attempt ->
            DirectUdpRetryPolicy.reservePort(55436, attempt) { requested ->
                asked.add(requested)
                returned[attempt - 1]
            }
        }
        assertEquals(3, asked.size)
        assertEquals(listOf(55436, 0, 0), asked)
        assertEquals(listOf(55436, 50008, 50009), results)
    }

    @Test fun internetFirstUsesForwardedPortThenEphemeralRetries() {
        val wanted = mutableListOf<Int>()
        for (attempt in 1..3) {
            DirectUdpRetryPolicy.reservePort(55436 + 1, attempt) {
                wanted.add(it)
                if (attempt == 1) throw IllegalStateException("busy")
                40000 + attempt
            }
        }
        assertEquals(listOf(55437, 0, 0), wanted)
    }

    @Test fun reservationExceptionDoesNotLeaveUnboundedRetry() {
        assertEquals(3, DirectUdpRetryPolicy.ATTEMPTS)
        assertEquals(0, DirectUdpRetryPolicy.reservePort(0, 1) {
            throw java.net.BindException("port blocked")
        })
    }
}
