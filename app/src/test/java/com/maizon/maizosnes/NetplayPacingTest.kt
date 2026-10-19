package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayPacingTest {
    private val ntsc = 16_666_667L

    @Test fun lanUsesSmallInputBuffer() {
        val lead = NetplayPacing.suggestFrameBuffer(
            longArrayOf(1_000_000L, 1_500_000L, 2_000_000L), ntsc, NetworkMode.LAN_AUTO)
        assertTrue("LAN should not add an excessive input delay", lead in 3..5)
    }

    @Test fun remoteTwoHundredMillisecondRttIsBufferedRatherThanPerFrameBlocked() {
        val lead = NetplayPacing.suggestFrameBuffer(
            longArrayOf(180_000_000L, 195_000_000L, 200_000_000L),
            ntsc, NetworkMode.RELAY
        )
        assertTrue("Must cover one-way latency and jitter", lead in 8..12)
    }


    @Test fun latencyPreferenceAlwaysTradesDelayForResilience() {
        val samples = longArrayOf(250_000_000L, 260_000_000L, 280_000_000L)
        val fastest = NetplayPacing.suggestFrameBuffer(samples, ntsc,
            NetworkMode.RELAY, NetplayLatencyMode.RESPONSIVE)
        val balanced = NetplayPacing.suggestFrameBuffer(samples, ntsc,
            NetworkMode.RELAY, NetplayLatencyMode.BALANCED)
        val stable = NetplayPacing.suggestFrameBuffer(samples, ntsc,
            NetworkMode.RELAY, NetplayLatencyMode.STABLE)
        assertTrue(fastest <= balanced)
        assertTrue(balanced < stable)
    }

    @Test fun slowInternetBufferIsBoundedAndCorrectAcrossPeers() {
        assertEquals(48, NetplayPacing.suggestFrameBuffer(
            longArrayOf(1_600_000_000L), ntsc, NetworkMode.RELAY))
        assertTrue(NetplayPacing.suggestFrameBuffer(
            longArrayOf(500_000_000L), ntsc, NetworkMode.INTERNET) >= 19)
    }
}
