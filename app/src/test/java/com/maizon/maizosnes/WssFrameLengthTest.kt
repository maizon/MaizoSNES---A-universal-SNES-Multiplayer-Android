package com.maizon.maizosnes

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WssFrameLengthTest {
    private fun encoded(vararg values: Int): ByteArrayInputStream =
        ByteArrayInputStream(values.map { it.toByte() }.toByteArray())

    @Test fun controlFramesRejectOversizePingPongAndClose() {
        for (opcode in 8..10) {
            WssFrameLength.validateControl(opcode, 125)
            assertThrows(IOException::class.java) {
                WssFrameLength.validateControl(opcode, 126)
            }
            assertThrows(IOException::class.java) {
                WssFrameLength.validateControl(opcode, 65536)
            }
        }
    }

    @Test fun closeFrameRejectsOneBytePayload() {
        WssFrameLength.validateControl(8, 0)
        WssFrameLength.validateControl(8, 2)
        assertThrows(IOException::class.java) {
            WssFrameLength.validateControl(8, 1)
        }
        // Binary data frames may legitimately reach the 64 KiB relay limit.
        WssFrameLength.validateControl(2, 65536)
    }

    @Test fun sixteenBitLengthMustBeCanonicalAndComplete() {
        assertEquals(126, WssFrameLength.read16(encoded(0, 126)))
        assertEquals(65535, WssFrameLength.read16(encoded(255, 255)))
        assertThrows(IOException::class.java) { WssFrameLength.read16(encoded(0, 125)) }
        assertThrows(EOFException::class.java) { WssFrameLength.read16(encoded(0)) }
    }

    @Test fun accepts64KiBExtendedLength() {
        assertEquals(65536, WssFrameLength.read64(encoded(0, 0, 0, 0, 0, 1, 0, 0)))
    }

    @Test fun rejectsOversizedExtendedLength() {
        assertThrows(IOException::class.java) {
            WssFrameLength.read64(encoded(0, 0, 0, 0, 0, 1, 0, 1))
        }
        assertThrows(IOException::class.java) {
            WssFrameLength.read64(encoded(128, 0, 0, 0, 0, 1, 0, 0))
        }
    }

    @Test fun rejectsNoncanonicalExtendedLength() {
        assertThrows(IOException::class.java) {
            WssFrameLength.read64(encoded(0, 0, 0, 0, 0, 0, 255, 255))
        }
    }

    @Test fun rejectsTruncatedExtendedLength() {
        assertThrows(EOFException::class.java) {
            WssFrameLength.read64(encoded(0, 0, 0, 0, 0, 1))
        }
    }
}
