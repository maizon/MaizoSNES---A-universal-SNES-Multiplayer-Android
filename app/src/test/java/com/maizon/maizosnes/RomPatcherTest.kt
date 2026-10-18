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
    @Test fun bpsChecksumsAndReconstructsTarget() {
        val source = byteArrayOf(1, 2, 3, 4)
        val expected = byteArrayOf(1, 9, 8, 4)
        val prefix = java.io.ByteArrayOutputStream()
        prefix.write("BPS1".toByteArray(Charsets.US_ASCII))
        // Source 4, destination 4, metadata 0.
        prefix.write(byteArrayOf(0x84.toByte(), 0x84.toByte(), 0x80.toByte()))
        // SourceRead length=1, TargetRead length=2, SourceRead length=1.
        prefix.write(byteArrayOf(0x80.toByte(), 0x85.toByte(), 9, 8, 0x80.toByte()))
        fun add32(checksum: Long) {
            for (shift in 0..3) prefix.write((checksum shr (shift * 8) and 255).toInt())
        }
        fun crc(bytes: ByteArray): Long =
            java.util.zip.CRC32().apply { update(bytes) }.value
        add32(crc(source))
        add32(crc(expected))
        add32(crc(prefix.toByteArray()))
        val result = RomPatcher.apply(source, prefix.toByteArray())
        assertArrayEquals(expected, result)
        assertThrows(IllegalArgumentException::class.java) {
            RomPatcher.apply(byteArrayOf(1, 2, 3, 5), prefix.toByteArray())
        }
    }
    @Test fun rejectsUnknownPatchAndOversize() {
        assertThrows(IllegalStateException::class.java) {
            RomPatcher.apply(byteArrayOf(1), byteArrayOf(0, 1))
        }
    }
}
