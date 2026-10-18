package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import java.security.MessageDigest

class RomDatabaseTest {
    @Test fun exactChecksumSelectsCorrectRegionAndRejectsWrongRevision() {
        val bytes = ByteArray(2048) { (it * 13).toByte() }
        val crc = CRC32().apply { update(bytes) }.value.toString(16)
            .uppercase().padStart(8, '0')
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02X".format(it) }
        val dat = """
            clrmamepro (
               name "SNES"
            )
            game (
               name "Sample Adventure (USA)"
               region "USA"
               rom ( name "Sample Adventure (USA).sfc" size 2048 crc $crc md5 AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA sha1 $sha1 )
            )
            game (
               name "Sample Adventure (Europe)"
               region "Europe"
               rom ( name "Sample Adventure (Europe).sfc" size 2048 crc DEADBEEF md5 AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA sha1 $sha1 )
            )
        """.trimIndent()
        val file = File.createTempFile("maizosnes-id", ".sfc")
        try {
            file.writeBytes(bytes)
            val fingerprint = RomDatabase.fingerprint(file)
            assertEquals("Sample Adventure (USA)",
                RomDatabase.parseMatch(dat, fingerprint)?.title)
            assertEquals("USA", RomDatabase.parseMatch(dat, fingerprint)?.region)
            assertNull(RomDatabase.parseMatch(dat,
                fingerprint.copy(sha1 = "A".repeat(40))))
            // Headered SNES dump must produce the same normalized identity.
            file.writeBytes(ByteArray(512) + bytes)
            val headered = RomDatabase.fingerprint(file)
            assertEquals(fingerprint, headered)
        } finally {
            file.delete()
        }
    }
}
