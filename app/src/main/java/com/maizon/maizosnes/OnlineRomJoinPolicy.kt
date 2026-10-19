package com.maizon.maizosnes

/** Flag permits attempting to join; the SSL socket independently verifies the certificate. */
internal object OnlineRomJoinPolicy {
    fun canJoin(hasRom: Boolean, matchingRom: Boolean, receiveEnabled: Boolean, relayTls: Boolean) =
        (hasRom && matchingRom) || (receiveEnabled && relayTls)
}
