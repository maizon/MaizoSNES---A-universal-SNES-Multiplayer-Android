package com.maizon.maizosnes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Strictly local avatar storage. No Railway upload, no decoding on the emulator thread. */
internal object ProfileAvatarStore {
    private const val MAX_BYTES = 65536
    private fun path(context: Context): File =
        File(context.filesDir, "profile/avatar.webp")
    fun delete(context: Context) { path(context).delete() }
    fun load(context: Context): Bitmap? =
        runCatching { path(context).takeIf { it.isFile }?.readBytes()?.let(::decodeSafe) }.getOrNull()
    /** Probe dimensions before allocating pixels; a tiny compressed image may be enormous. */
    fun decodeSafe(encoded: ByteArray): Bitmap? {
        if (encoded.size !in 16..MAX_BYTES) return null
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, probe)
        if (probe.outWidth !in 1..256 || probe.outHeight !in 1..256) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, max(probe.outWidth, probe.outHeight) / 96)
        }
        return runCatching {
            BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
                ?.let { bitmap ->
                    if (bitmap.width <= 96 && bitmap.height <= 96) bitmap
                    else Bitmap.createScaledBitmap(bitmap, 96, 96, true).also {
                        if (it !== bitmap) bitmap.recycle()
                    }
                }
        }.getOrNull()
    }
    fun save(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 192) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use { source ->
            BitmapFactory.decodeStream(source, null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        return try {
            val square = minOf(decoded.width, decoded.height)
            val cut = Bitmap.createBitmap(decoded,
                (decoded.width - square) / 2, (decoded.height - square) / 2, square, square)
            val small = Bitmap.createScaledBitmap(cut, 96, 96, true)
            val output = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30)
                Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            if (!small.compress(format, 75, output) || output.size() > MAX_BYTES) return null
            val destination = path(context)
            destination.parentFile?.mkdirs()
            val tmp = File(destination.parentFile, destination.name + ".tmp")
            tmp.writeBytes(output.toByteArray())
            if (!tmp.renameTo(destination)) { tmp.copyTo(destination, overwrite = true); tmp.delete() }
            small
        } finally {
            decoded.recycle()
        }
    }
}
