package com.maizon.maizosnes

/** Seam for deterministic JVM netplay handshake tests.
 * In production this is backed by the same Gekko C++ JNI engine.
 * WebRTC uses its existing separate native adapter unchanged.
 */
internal interface DirectRollbackDriver {
    fun configureLimitedSaving(enabled: Boolean)
    fun start(localPort: Int, remoteIp: String, remotePort: Int,
              player: Int, stateBytes: Int, prediction: Int, delay: Int): Boolean
    fun tick(localInput: Int, callbacks: RollbackCallbacks): Int
    fun stats(): IntArray
    fun setLocalDelay(frames: Int): Boolean
    fun setReplay(enabled: Boolean)
    fun setPresentationStride(stride: Int)
    fun stop()
}

internal object NativeDirectRollbackDriver : DirectRollbackDriver {
    override fun configureLimitedSaving(enabled: Boolean) =
        GekkoNative.nativeConfigureLimitedSaving(enabled)
    override fun start(localPort: Int, remoteIp: String, remotePort: Int,
                       player: Int, stateBytes: Int, prediction: Int, delay: Int) =
        GekkoNative.nativeStart(localPort, remoteIp, remotePort,
            player, stateBytes, prediction, delay)
    override fun tick(localInput: Int, callbacks: RollbackCallbacks) =
        GekkoNative.nativeTick(localInput, callbacks)
    override fun stats() = GekkoNative.nativeStats()
    override fun setLocalDelay(frames: Int) =
        GekkoNative.nativeSetLocalDelay(frames)
    override fun setReplay(enabled: Boolean) =
        NativeBridge.nativeSetRollbackReplaying(enabled)
    override fun setPresentationStride(stride: Int) =
        NativeBridge.nativeSetNetplayPresentationStride(stride)
    override fun stop() = GekkoNative.nativeStop()
}
