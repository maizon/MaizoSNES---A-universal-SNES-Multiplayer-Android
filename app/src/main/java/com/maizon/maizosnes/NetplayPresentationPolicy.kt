package com.maizon.maizosnes

/**
 * Do not conflate network RTT with CPU/GPU load: a distant peer does not
 * justify dropping half of the rendered frames. Degrade presentation only
 * after repeated evidence that local netplay tick time exceeds the budget.
 * Native emulation, audio, inputs and rollback remain at full cadence.
 */
internal class NetplayPresentationPolicy(
    private val lowEnd: Boolean = false
) {
    private var overloaded = 0
    private var recovered = 0
    private var stride = 1

    fun offer(rttMs: Double, tickMs: Double = 0.0,
              frameBudgetMs: Double = 16.67): Int {
        if (!tickMs.isFinite() || !frameBudgetMs.isFinite() ||
            frameBudgetMs <= 0.0 || tickMs <= 0.0) return stride
        if (tickMs >= frameBudgetMs * (if (lowEnd) 0.82 else 0.88)) {
            overloaded++
            recovered = 0
            if (overloaded >= (if (lowEnd) 2 else 3)) stride = 2
        } else if (tickMs <= frameBudgetMs * 0.66) {
            recovered++
            overloaded = 0
            if (recovered >= (if (lowEnd) 6 else 5)) stride = 1
        } else {
            overloaded = 0
            recovered = 0
        }
        return stride
    }
}
