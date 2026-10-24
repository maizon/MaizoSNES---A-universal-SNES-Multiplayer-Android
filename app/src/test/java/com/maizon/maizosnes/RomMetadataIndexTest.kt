package com.maizon.maizosnes

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RomMetadataIndexTest {
    @Test fun cachesMetadataAndRechecksChangedFile() {
        val folder = Files.createTempDirectory("maizo-rom-index-").toFile()
        try {
            val games = java.io.File(folder, "roms").apply { mkdirs() }
            val index = java.io.File(folder, "index.bin")
            val rom = java.io.File(games, "Example.nes")
            val data = ByteArray(32).apply {
                this[0] = 0x4e
                this[1] = 0x45
                this[2] = 0x53
                this[3] = 0x1a
            }
            rom.writeBytes(data)
            val first = RomMetadataIndex.scan(games, index)
            assertEquals(1, first.inspected)
            assertEquals(listOf("Example.nes"), first.names)
            val second = RomMetadataIndex.scan(games, index)
            assertEquals(1, second.reused)
            assertEquals(0, second.inspected)
            rom.appendBytes(byteArrayOf(1))
            val third = RomMetadataIndex.scan(games, index)
            assertEquals(1, third.inspected)
            assertEquals("NES • NTSC", third.details["Example.nes"]?.region)
            rom.delete()
            val removed = RomMetadataIndex.scan(games, index)
            assertTrue(removed.names.isEmpty())
        } finally { folder.deleteRecursively() }
    }

    @Test fun ignoresCorruptIndexAndChecksUnknownMtime() {
        val folder = Files.createTempDirectory("maizo-corrupt-index-").toFile()
        try {
            val index = java.io.File(folder, "index.bin")
            index.writeText("garbled")
            val games = java.io.File(folder, "roms").apply { mkdirs() }
            val rom = java.io.File(games, "fallback.sfc")
            rom.writeBytes(ByteArray(12))
            assertEquals(1, RomMetadataIndex.scan(games, index).inspected)
            assertTrue(RomMetadataIndex.shouldInspect(1, 0, 1, 0))
            assertFalse(RomMetadataIndex.shouldInspect(100, 1, 100, 1))
        } finally { folder.deleteRecursively() }
    }
}
