package com.maizon.maizosnes

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Default LAN game join entry-point. Unlike an eager NetplaySession.host(),
 * this listener does NOT call nativeSetNetplayMode(true) while waiting for
 * a peer: the host can keep playing with P1 and P2-P4 remote pads.
 *
 * An accepted socket is transferred to the existing MZNP ROM/consent
 * handshake. Only then does deterministic GekkoNet/Lockstep take ownership.
 */
internal class LanGameJoinListener(
    port: Int = 55435,
    private val onIncoming: (Socket) -> Unit
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val listener = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(port))
        soTimeout = 300
    }
    init {
        Thread({
            try {
                while (!closed.get()) {
                    try {
                        val accepted = listener.accept()
                        if (!accepted.inetAddress.isSiteLocalAddress) {
                            accepted.close()
                            continue
                        }
                        // Transfer ownership to the session. Do not close accepted here.
                        close()
                        onIncoming(accepted)
                        break
                    } catch (_: SocketTimeoutException) { }
                }
            } catch (_: SocketException) {
                // Listener was closed on room teardown.
            } finally { close() }
        }, "Maizo-LAN-TCP-Join").apply { isDaemon = true; start() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listener.close()
    }
}
