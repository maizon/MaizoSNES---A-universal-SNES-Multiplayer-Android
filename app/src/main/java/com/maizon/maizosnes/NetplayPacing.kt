package com.maizon.maizosnes

import kotlin.math.ceil

/**
 * Predicts enough INPUT LEAD to cover one-way transport latency and modest
 * jitter. The relay can be an extra hop but it is not a round-trip per frame.
 * Lead frames trade controller latency for steady frame pacing.
 * No rollback or speculation is performed by the emulator.
 */
internal object NetplayPacing {
    fun suggestFrameBuffer(
        rttSamplesNs: LongArray,
        periodNs: Long,
        mode: NetworkMode,
        latencyMode: NetplayLatencyMode = NetplayLatencyMode.BALANCED
    ): Int {
        require(periodNs > 0L)
        val sample = rttSamplesNs.filter { it > 0L }.maxOrNull() ?: 0L
        // 0.6 RTT > expected 0.5 RTT one-way, 30ms jitter reserve.
        // Small LAN latency gets a smaller delay; Internet/relay gets a floor.
        val targetNs = when (latencyMode) {
            NetplayLatencyMode.RESPONSIVE -> sample.toDouble() * 0.52 + 10_000_000.0
            NetplayLatencyMode.BALANCED -> sample.toDouble() * 0.60 + 30_000_000.0
            NetplayLatencyMode.STABLE -> sample.toDouble() * 0.73 + 80_000_000.0
        }
        val recommended = ceil(targetNs / periodNs.toDouble()).toInt() + 1
        val minimum = when (mode) {
            NetworkMode.LOCAL -> 2
            NetworkMode.LAN_AUTO, NetworkMode.LAN_MANUAL -> 3
            NetworkMode.AUTO -> 4
            NetworkMode.INTERNET -> 5
            NetworkMode.RELAY -> 6
        }
        return recommended.coerceIn(minimum, 48)
    }
}
