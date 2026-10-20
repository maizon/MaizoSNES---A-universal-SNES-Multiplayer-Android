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
    /** Available only during MZNP v3 setup, before the first netplay frame. */
    external fun nativeApplyOnlineCheats(codes: Array<String>): Int
    external fun nativeSetPlayer2Input(bits: Int)
    external fun nativeSetNetplayMode(enabled: Boolean)
    external fun nativeNetplayStep(player1: Int, player2: Int): Int
    /** Mutes speculative rollback re-simulation; preserves stable audio/video normally. */
    external fun nativeSetRollbackReplaying(enabled: Boolean)
    /** 1: show all; 2: skip half video presents without skipping SNES frames. */
    external fun nativeSetNetplayPresentationStride(stride: Int)
    external fun nativeGetFramePeriodNs(): Long
    external fun nativeNetplayStateHash(): Long
    external fun nativeSetPaused(paused: Boolean)
    /** Discard outdated PCM written while multiplayer ran without audible output. */
    external fun nativeDiscardAudioQueue()
    /** Cheap optional RGB565 filters (0 original, 1 scanline, 2 soft, 3 combined). */
    external fun nativeSetVideoFilter(mode: Int)
    /** Nearest-neighbor output framebuffer multiplier (1..4), not SNES PPU upscale. */
    external fun nativeSetOutputScale(scale: Int)
    /** Enables native frame export for GLES, disabling direct CPU SurfaceView output. */
    external fun nativeSetGpuEnabled(enabled: Boolean)
    /** Copies RGB565 frame into direct ByteBuffer and sets width, height, frame revision. */
    external fun nativeCopyGpuFrame(buffer: java.nio.ByteBuffer, metadata: IntArray): Boolean
    /** Cumulative counters: frames, emuNs, presentedFrames, presentNs, targetFrameNs, audioFramesQueued. */
    external fun nativeGetPerfCounters(): LongArray
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
