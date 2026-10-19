package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RomFingerprintTest {
    @Test fun knownSha256VectorIsStableAcrossLocalAndReceivedPaths() {
        val bytes = "abc".toByteArray(Charsets.UTF_8)
        assertEquals("ba7816bf8f01cfea", RomFingerprint.prefix(bytes))
        assertEquals(RomFingerprint.prefix(bytes), RomFingerprint.prefix(bytes.copyOf()))
    }

    @Test fun receivedRomFingerprintMatchesOnDiskRoomHash() {
        val bytes = ByteArray(32769) { (it % 251).toByte() }
        val file = File.createTempFile("maizosnes-rom-hash", ".sfc")
        try {
            file.writeBytes(bytes)
            val fromFile = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes()).take(8)
                .joinToString("") { "%02x".format(it) }
            assertEquals(fromFile, RomFingerprint.prefix(bytes))
            assertNotEquals(fromFile, RomFingerprint.prefix(bytes + 0.toByte()))
        } finally {
            file.delete()
        }
    }
}
