package com.maizon.maizosnes

/**
 * Throttle presentation on persistent high RTT only.
 * No SNES simulation, audio, input, netcode or serialization is skipped.
 */
internal class NetplayPresentationPolicy {
    private var high = 0
    private var low = 0
    private var stride = 1

    fun offer(rttMs: Double): Int {
        if (!rttMs.isFinite() || rttMs <= 0) return stride
        if (rttMs > 150) {
            high++
            low = 0
            if (high >= 3) stride = 2
        } else if (rttMs < 110) {
            low++
            high = 0
            if (low >= 5) stride = 1
        } else {
            high = 0
            low = 0
        }
        return stride
    }
}
