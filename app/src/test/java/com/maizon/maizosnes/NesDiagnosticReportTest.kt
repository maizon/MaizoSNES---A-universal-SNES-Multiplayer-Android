package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class NesDiagnosticReportTest {
    @Test fun everyNativeReturnHasReadableMessage() {
        for (status in listOf(0, -1, -2, -3, -4, -5, -100)) {
            val explanation = NesDiagnosticReport.describe(status)
            assertTrue(explanation.length >= 20)
        }
        assertTrue(NesDiagnosticReport.describe(0).contains("aprovado"))
        assertTrue(NesDiagnosticReport.describe(-5).contains("idênticos"))
    }
}
