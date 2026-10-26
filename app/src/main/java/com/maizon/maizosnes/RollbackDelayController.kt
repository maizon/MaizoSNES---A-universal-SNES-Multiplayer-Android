package com.maizon.maizosnes

/**
 * Adjust GekkoNet input delay conservatively to prevent repeated expensive
 * rollback/re-simulation during mobile jitter bursts, without changing the
 * deterministic Snes9x core or the existing lockstep wire protocol.
 *
 * The two peers choose local input delays independently, as supported by
 * gekko_set_local_delay. Time/sample units are monotonic nanoseconds/ms.
 */
internal class RollbackDelayController(
    profile: NetplayLatencyMode, private val lowEnd: Boolean = false
) {
    private val minimum = when (profile) {
        NetplayLatencyMode.RESPONSIVE -> 0
        NetplayLatencyMode.BALANCED -> 1
        NetplayLatencyMode.STABLE -> 3
    }
    private val maximum = when (profile) {
        NetplayLatencyMode.RESPONSIVE -> 2
        NetplayLatencyMode.BALANCED -> 5
        NetplayLatencyMode.STABLE -> 6
    }
    var delay: Int = if (lowEnd) maxOf(2, minimum) else minimum
        private set
    private var previousTime: Long = 0
    private var lastRollbacks: Long = 0
    private var quietWindows = 0
    private val lowEndFloor = if (lowEnd) maxOf(2, minimum) else minimum

    fun offer(nowNs: Long, totalRollbacks: Long,
              rttMs: Double, jitterMs: Double): Int? {
        if (previousTime == 0L) {
            previousTime = nowNs
            lastRollbacks = totalRollbacks
            return null
        }
        if (nowNs < previousTime ||
            nowNs - previousTime < 5_000_000_000L) return null
        val newRollbacks = (totalRollbacks - lastRollbacks).coerceAtLeast(0)
        previousTime = nowNs
        lastRollbacks = totalRollbacks
        // Lowend trades a little local input latency for fewer costly
        // correction bursts; the default controller is unchanged.
        val problematic = newRollbacks >= (if (lowEnd) 2 else 4) ||
            (newRollbacks >= 1 &&
                (jitterMs > (if (lowEnd) 18.0 else 25.0) ||
                    rttMs > (if (lowEnd) 150.0 else 180.0)))
        val next = if (problematic) {
            quietWindows = 0
            (delay + 1).coerceAtMost(maximum)
        } else {
            if (newRollbacks == 0L && jitterMs < 12.0) quietWindows++
            else quietWindows = 0
            if (quietWindows >= (if (lowEnd) 7 else 5)) {
                quietWindows = 0
                (delay - 1).coerceAtLeast(lowEndFloor)
            } else delay
        }
        if (next == delay) return null
        delay = next
        return next
    }
}
