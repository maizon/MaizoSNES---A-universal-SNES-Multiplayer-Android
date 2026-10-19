package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class RollbackModeTest {
    @Test fun rollbackDefaultAndViability() {
        assertEquals(NetcodeMode.ROLLBACK, NetcodeMode.values().first())
        assertNull(RollbackFeasibility.rejection(RollbackBenchmark(900_000, 2.0, 3.0, true)))
        assertNotNull(RollbackFeasibility.rejection(RollbackBenchmark(900_000, 7.0, 7.0, true)))
        assertNotNull(RollbackFeasibility.rejection(RollbackBenchmark(900_000, 1.0, 1.0, false)))
        assertNotNull(RollbackFeasibility.rejection(RollbackBenchmark(20000000, 1.0, 1.0, true)))
    }
}
