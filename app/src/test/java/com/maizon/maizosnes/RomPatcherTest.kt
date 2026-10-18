package com.maizon.maizosnes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RomPatcherTest {
    @Test fun ipsWritesOnlyCopy() {
        val source = byteArrayOf(1, 2, 3, 4)
        val ips = byteArrayOf(
            80, 65, 84, 67, 72, // PATCH
            0, 0, 1, 0, 2, 9, 8, // offset 1, 2-byte write
            69, 79, 70 // EOF
        )
        val result = RomPatcher.apply(source, ips)
        assertArrayEquals(byteArrayOf(1, 9, 8, 4), result)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), source)
    }
    @Test fun rejectsUnknownPatchAndOversize() {
        assertThrows(IllegalStateException::class.java) {
            RomPatcher.apply(byteArrayOf(1), byteArrayOf(0, 1))
        }
    }
}
