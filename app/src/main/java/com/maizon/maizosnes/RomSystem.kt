package com.maizon.maizosnes

import java.io.File

/** SNES remains default; NES/Famicom always chooses the separate FCEUmm core. */
internal object RomSystem {
    private val nesExtensions = setOf("nes", "unf", "unif", "fds")
    val acceptedExtensions = setOf("sfc", "smc") + nesExtensions

    fun isNes(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in nesExtensions

    fun supported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in acceptedExtensions

    fun platform(name: String): String =
        if (isNes(name)) "Nintendo NES" else "Super Nintendo"

    fun details(file: File): SnesRomHeader.Details {
        if (!isNes(file.name)) return SnesRomHeader.inspect(file)
        val fallback = SnesRomHeader.Details(file.nameWithoutExtension, "NES / Famicom", null)
        if (!file.extension.equals("nes", ignoreCase = true)) return fallback
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                if (input.read(header) != header.size ||
                    !header.copyOfRange(0, 4).contentEquals(
                        byteArrayOf(0x4E, 0x45, 0x53, 0x1A)))
                    fallback.copy(warning = "Cabeçalho iNES não reconhecido.")
                else fallback.copy(region = if (header[9].toInt() and 1 != 0)
                    "NES • PAL" else "NES • NTSC")
            }
        }.getOrDefault(fallback)
    }
}
