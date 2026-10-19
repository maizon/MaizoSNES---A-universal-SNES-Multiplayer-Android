package com.maizon.maizosnes

import java.security.MessageDigest

/** Same 8-byte SHA-256 room fingerprint for local and authorized received ROMs. */
internal object RomFingerprint {
    fun prefix(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).take(8).joinToString("") { "%02x".format(it) }
}
