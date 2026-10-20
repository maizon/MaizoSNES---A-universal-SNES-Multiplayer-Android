package com.maizon.maizosnes

/** Android Activity visibility must never interrupt an active deterministic
 * netplay worker, even when the Surface or AudioTrack are unavailable.
 * Only offline play pauses when the display leaves the foreground.
 */
internal object NetplayBackgroundPolicy {
    fun pauseCore(netplayActive: Boolean, visibleAndRunnable: Boolean): Boolean =
        !netplayActive && !visibleAndRunnable

    /** Partial wake lock and Wi-Fi lock follow user preference only.
     * Turning the service off does not forcibly disconnect an active game.
     */
    fun needsForegroundService(sessionActive: Boolean, enabled: Boolean): Boolean =
        sessionActive && enabled
}
