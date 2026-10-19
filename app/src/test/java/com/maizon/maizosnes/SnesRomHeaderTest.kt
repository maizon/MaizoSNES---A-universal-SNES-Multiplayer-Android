package com.maizon.maizosnes

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SnesRomHeaderTest {
    @Test fun readsInternalTitleAndRegion() {
        val file = File.createTempFile("snes-header-test", ".sfc")
        try {
            val rom = ByteArray(0x8000)
            val title = "MAIZO TEST GAME"
            title.toByteArray(Charsets.US_ASCII).copyInto(rom, 0x7FC0)
            rom[0x7FC0 + 25] = 1
            rom[0x7FC0 + 28] = 0xCB.toByte()
            rom[0x7FC0 + 29] = 0xED.toByte()
            rom[0x7FC0 + 30] = 0x34
            rom[0x7FC0 + 31] = 0x12
            file.writeBytes(rom)
            val details = SnesRomHeader.inspect(file)
            assertEquals(title, details.title)
            assertEquals("América do Norte", details.region)
            assertNull(details.warning)
        } finally { file.delete() }
    }

    @Test fun doesNotTrustMissingHeader() {
        val file = File.createTempFile("snes-empty-test", ".sfc")
        try {
            file.writeBytes(ByteArray(0x8000))
            val result = SnesRomHeader.inspect(file)
            assertEquals(file.nameWithoutExtension, result.title)
            assertNotNull(result.warning)
        } finally { file.delete() }
    }
}
