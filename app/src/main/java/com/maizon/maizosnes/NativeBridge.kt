package com.maizon.maizosnes

import android.view.Surface

object NativeBridge {
    init {
        System.loadLibrary("native-lib")
    }

    external fun nativeInit(appDataPath: String): Int
    external fun nativeLoadRom(bytes: ByteArray, displayName: String): Int
    external fun nativeSetSurface(surface: Surface?)
    external fun nativeSetInput(bits: Int)
    external fun nativeSetPaused(paused: Boolean)
    /** Copies interleaved stereo PCM16 values into [buffer]. Returns the number of values written. */
    external fun nativeReadAudio(buffer: ShortArray): Int
    external fun nativeGetAudioSampleRate(): Int
    external fun nativeShutdown()
}
