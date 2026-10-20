package com.maizon.maizosnes

import java.net.DatagramSocket

/**
 * Bounded direct-UDP candidate plan. A port change can address bind/race
 * failures; it CANNOT bypass router AP/client isolation or UDP firewall.
 * Each side must negotiate the selected port over the same MZNP TCP stream.
 */
internal object DirectUdpRetryPolicy {
    const val ATTEMPTS = 3
    const val DISCOVERY_PORT = 55436

    fun candidatePreferred(preferred: Int, attempt: Int): Int {
        require(attempt in 1..ATTEMPTS)
        return if (attempt == 1 && preferred in 1024..65535 &&
            preferred != DISCOVERY_PORT) preferred else 0
    }

    fun reservePort(preferred: Int, attempt: Int,
                    bind: (Int) -> Int = { wanted ->
                        DatagramSocket(wanted).use { it.localPort }
                    }): Int {
        val candidate = candidatePreferred(preferred, attempt)
        val port = runCatching { bind(candidate) }.getOrDefault(0)
        return port.takeIf { it in 1024..65535 && it != DISCOVERY_PORT } ?: 0
    }
}
