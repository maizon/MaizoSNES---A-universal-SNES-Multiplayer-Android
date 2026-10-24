package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class NesRollbackSafetyTest {
    @Test fun validatesRepeatableSnapshots() {
        var loads = 0
        val state = ByteArray(1024) { 7 }
        assertTrue(NesRollbackSafety.isSafe(state,
            { state.copyOf() }, { loads++; 0 }))
        assertEquals(4, loads)
    }
    @Test fun rejectsLargeOrChangingSnapshotsWithoutThrowing() {
        assertFalse(NesRollbackSafety.isSafe(ByteArray(NesRollbackSafety.MAX_NES_STATE_BYTES + 1),
            { ByteArray(32) }, { 0 }))
        assertFalse(NesRollbackSafety.isSafe(ByteArray(64),
            { ByteArray(63) }, { 0 }))
        assertFalse(NesRollbackSafety.isSafe(ByteArray(64),
            { ByteArray(64) }, { -1 }))
        assertFalse(NesRollbackSafety.isSafe(ByteArray(64),
            { throw IllegalStateException("save failed") }, { 0 }))
    }
}
