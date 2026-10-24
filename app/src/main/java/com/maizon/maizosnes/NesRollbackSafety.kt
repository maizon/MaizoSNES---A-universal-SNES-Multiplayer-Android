package com.maizon.maizosnes

/**
 * Core-specific preflight before allocating GekkoNet rollback snapshots.
 * Runs only for NES; the established Snes9x rollback path is unchanged.
 * Results are local, and no packets or protocol bytes are introduced.
 */
internal object NesRollbackSafety {
    const val MAX_NES_STATE_BYTES = 2 * 1024 * 1024 + 28
    fun isSafe(initial: ByteArray?, save: () -> ByteArray?, load: (ByteArray) -> Int): Boolean {
        if (initial == null || initial.size !in 33..MAX_NES_STATE_BYTES) return false
        return try {
            repeat(3) {
                if (load(initial) != 0) return false
                val checkpoint = save() ?: return false
                if (checkpoint.size != initial.size) return false
            }
            // Ensure a deterministic starting state before negotiation.
            load(initial) == 0
        } catch (_: Exception) { false }
    }
}
