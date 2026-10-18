package com.maizon.maizosnes

import android.view.Surface

object NativeBridge {
    init { System.loadLibrary("native-lib") }

    external fun nativeInit(appDataPath: String): Int
    external fun nativeLoadRom(bytes: ByteArray, displayName: String): Int
    external fun nativeSetSurface(surface: Surface?)
    external fun nativeSetInput(bits: Int)
    /** Complete selected cheat set, rebuilt atomically. Negative value means rejection. */
    external fun nativeApplyCheats(codes: Array<String>): Int
    external fun nativeSetPlayer2Input(bits: Int)
    external fun nativeSetNetplayMode(enabled: Boolean)
    external fun nativeNetplayStep(player1: Int, player2: Int): Int
    external fun nativeGetFramePeriodNs(): Long
    external fun nativeNetplayStateHash(): Long
    external fun nativeSetPaused(paused: Boolean)
    /** Applies an actual Snes9x libretro option through the frontend bridge. */
    external fun nativeSetCoreOption(key: String, value: String)
    /** Copies interleaved stereo PCM16 values into buffer. Returns the number of values written. */
    external fun nativeReadAudio(buffer: ShortArray): Int
    external fun nativeGetAudioSampleRate(): Int
    external fun nativeResetGame(): Int
    external fun nativeSerializeState(): ByteArray?
    external fun nativeUnserializeState(state: ByteArray): Int
    external fun nativeShutdown()
}
