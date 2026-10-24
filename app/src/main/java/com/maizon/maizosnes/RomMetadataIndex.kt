package com.maizon.maizosnes

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Persistent, bounded ROM-header cache. Only size/mtime-changed files are
 * re-inspected, and no SHA-256 hashing or game bytes are read during browsing.
 *
 * All operations are called from Dispatchers.IO, not the Compose/UI thread.
 * The index contains only local filenames and cartridge metadata; no ROM data.
 */
internal object RomMetadataIndex {
    private const val MAGIC = 0x4d5a4958 // MZIX
    private const val VERSION = 1
    private const val MAX_GAMES = 5000
    private const val MAX_INDEX_BYTES = 3L * 1024 * 1024

    data class Scan(
        val names: List<String>,
        val details: Map<String, SnesRomHeader.Details>,
        val reused: Int,
        val inspected: Int
    )

    private data class Cached(
        val length: Long,
        val modified: Long,
        val details: SnesRomHeader.Details
    )

    internal fun shouldInspect(length: Long, modified: Long,
        previousLength: Long?, previousModified: Long?): Boolean =
        previousLength != length || previousModified != modified ||
            length <= 0 || modified <= 0

    @Synchronized
    fun scan(romFolder: File, indexFile: File): Scan {
        val files = romFolder.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && RomSystem.supported(it.name) }
            ?.sortedWith(compareBy<File> { it.name.lowercase() }.thenBy { it.name })
            ?.take(MAX_GAMES)
            ?.toList() ?: emptyList()
        val previous = load(indexFile)
        val current = LinkedHashMap<String, Cached>(files.size)
        var reused = 0
        var inspected = 0
        files.forEach { file ->
            val old = previous[file.name]
            val len = file.length()
            val modified = file.lastModified()
            val details = if (old != null &&
                !shouldInspect(len, modified, old.length, old.modified)) {
                reused++
                old.details
            } else {
                inspected++
                RomSystem.details(file)
            }
            current[file.name] = Cached(len, modified, details)
        }
        if (current != previous) persist(indexFile, current)
        return Scan(current.keys.toList(),
            current.mapValues { it.value.details }, reused, inspected)
    }

    private fun load(indexFile: File): Map<String, Cached> {
        if (!indexFile.isFile || indexFile.length() !in 12L..MAX_INDEX_BYTES)
            return emptyMap()
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(indexFile))).use { input ->
                require(input.readInt() == MAGIC)
                require(input.readInt() == VERSION)
                val count = input.readInt()
                require(count in 0..MAX_GAMES)
                buildMap {
                    repeat(count) {
                        val name = input.readUTF()
                        val bytes = input.readLong()
                        val modified = input.readLong()
                        val title = input.readUTF()
                        val region = input.readUTF()
                        val warning = if (input.readBoolean()) input.readUTF() else null
                        if (name.isNotBlank() && name.length <= 255)
                            put(name, Cached(bytes, modified,
                                SnesRomHeader.Details(title, region, warning)))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persist(indexFile: File, current: Map<String, Cached>) {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val staging = File(indexFile.parentFile, indexFile.name + ".partial")
            try {
                DataOutputStream(BufferedOutputStream(FileOutputStream(staging))).use { out ->
                    out.writeInt(MAGIC)
                    out.writeInt(VERSION)
                    out.writeInt(current.size)
                    for ((name, item) in current) {
                        out.writeUTF(name.take(255))
                        out.writeLong(item.length)
                        out.writeLong(item.modified)
                        out.writeUTF(item.details.title.take(200))
                        out.writeUTF(item.details.region.take(100))
                        out.writeBoolean(item.details.warning != null)
                        item.details.warning?.let { out.writeUTF(it.take(400)) }
                    }
                }
                // Android's same-directory rename is atomic. Failure is harmless:
                // only a cache is lost, and the next scan re-reads headers.
                if (!staging.renameTo(indexFile))
                    staging.delete()
            } finally {
                if (staging.exists()) staging.delete()
            }
        }
    }
}
