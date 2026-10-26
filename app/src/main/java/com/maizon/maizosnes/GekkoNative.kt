package com.maizon.maizosnes

/** Native GekkoNet link is isolated from Snes9x and the stable lockstep bridge. */
internal object GekkoNative {
    init { System.loadLibrary("gekko_bridge") }
    external fun nativeApiVersion(): Int
    /** Applies to the next GekkoNet session, not to running games. */
    external fun nativeConfigureLimitedSaving(enabled: Boolean)
    /** Lowend ON applies to the next session; OFF keeps legacy ByteArray path. */
    external fun nativeConfigureFastSnapshots(enabled: Boolean)
    external fun nativeStart(localPort: Int, address: String, remotePort: Int,
        localPlayer: Int, stateBytes: Int, prediction: Int, delay: Int): Boolean
    external fun nativeTick(input: Int, callbacks: RollbackCallbacks): Int
    external fun nativeStartRtc(player: Int, stateBytes: Int,
        prediction: Int, delay: Int, port: WebRtcRollbackTransport): Boolean
    /** RTT ms, jitter 0.1ms, cumulative loads/rollbacks, advanced frames, connected, aborted. */
    external fun nativeStats(): IntArray
    /** Live per-peer delay; official GekkoNet local input scheduling. */
    external fun nativeSetLocalDelay(frames: Int): Boolean
    external fun nativeStop()
}
internal interface RollbackCallbacks {
    fun save(): ByteArray
    fun load(bytes: ByteArray): Boolean
    fun step(p1: Int, p2: Int, resimulation: Boolean): Boolean
}

/**
 * Extra callback methods resolved ONLY after enabling the Lowend preset.
 * GekkoNet owns the direct ByteBuffer and it is valid only until return.
 */
internal interface FastRollbackCallbacks : RollbackCallbacks {
    fun saveDirect(target: java.nio.ByteBuffer): Int
    fun loadDirect(source: java.nio.ByteBuffer, length: Int): Boolean
}
