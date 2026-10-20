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
