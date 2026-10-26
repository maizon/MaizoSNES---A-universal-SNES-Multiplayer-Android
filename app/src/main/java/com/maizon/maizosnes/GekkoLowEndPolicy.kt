package com.maizon.maizosnes

/**
 * User-selectable LOCAL performance preset. Never modify wire protocol,
 * save-state container, ROM hashes or the pinned emulator cores.
 *
 * OFF: exact historical default parameters and snapshot callbacks.
 * ON: direct snapshots into Gekko's native buffers + more conservative
 * adaptive local delay and optional lighter video presentation.
 */
internal object GekkoLowEndPolicy {
    fun effectiveNetcode(saved: NetcodeMode, enabled: Boolean): NetcodeMode =
        if (enabled) NetcodeMode.ROLLBACK else saved

    fun initialLocalDelay(mode: NetplayLatencyMode, enabled: Boolean): Int {
        val standard = when (mode) {
            NetplayLatencyMode.RESPONSIVE -> 0
            NetplayLatencyMode.BALANCED -> 1
            NetplayLatencyMode.STABLE -> 3
        }
        return if (enabled) maxOf(2, standard) else standard
    }

    fun preferLimitedSaving(saveLoadMs: Double, enabled: Boolean): Boolean =
        saveLoadMs > (if (enabled) 6.0 else 8.0)

    fun readyForUdp(stateLoaded: Boolean, nesSafe: Boolean,
        stateSize: Int, saveLoadMs: Double, hasIpv4: Boolean,
        closed: Boolean, enabled: Boolean): Boolean =
        stateLoaded && nesSafe && stateSize in 32..RollbackFeasibility.MAX_STATE_BYTES &&
            saveLoadMs.isFinite() &&
            saveLoadMs <= (if (enabled) 26.0 else 24.0) &&
            hasIpv4 && !closed

    fun message(enabled: Boolean): String =
        if (enabled) "GekkoNet Lowend ligado: snapshots JNI sem cópias, " +
            "atraso local adaptativo e apresentação leve quando necessário. " +
            "Só se aplica a sessões iniciadas agora."
        else "GekkoNet Lowend desligado: o próximo multiplayer usará " +
            "exatamente os parâmetros anteriores."
}
