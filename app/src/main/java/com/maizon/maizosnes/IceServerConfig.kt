package com.maizon.maizosnes

import livekit.org.webrtc.PeerConnection
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * STUN is public; authenticated TURN credentials are short-lived and arrive
 * from MaizoSNES's existing Railway HTTPS origin only. Never place Metered's
 * account-scoped API key in the Android package.
 */
internal object IceServerConfig {
    private val permitted = Regex(
        "^(stun|stuns|turn|turns):[A-Za-z0-9][A-Za-z0-9.:-]{1,240}(\\?transport=(udp|tcp))?$"
    )
    val stunUrls = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun.cloudflare.com:3478"
    )

    private fun validated(url: String): Boolean =
        url.length < 256 && permitted.matches(url)

    fun fromText(config: String): List<PeerConnection.IceServer> {
        // Format: one STUN/TURN URI per line. For authenticated TURN:
        // turn:my-host.example:3478|username|password
        return config.lineSequence().take(12).mapNotNull { raw ->
            val fields = raw.trim().split('|', limit = 3)
            val url = fields.firstOrNull()?.trim() ?: return@mapNotNull null
            if (!validated(url)) return@mapNotNull null
            if (url.startsWith("turn") &&
                (fields.size < 3 || fields[1].isEmpty() || fields[2].isEmpty() ||
                    fields[1].length > 120 || fields[2].length > 200)) return@mapNotNull null
            val builder = PeerConnection.IceServer.builder(url)
            if (fields.size >= 3) {
                builder.setUsername(fields[1])
                builder.setPassword(fields[2])
            }
            builder.createIceServer()
        }.toList()
    }

    fun parseProvider(json: String): List<PeerConnection.IceServer> {
        val obj = JSONObject(json)
        if (!obj.optBoolean("configured", false)) return emptyList()
        val entries = obj.optJSONArray("iceServers") ?: return emptyList()
        val servers = mutableListOf<PeerConnection.IceServer>()
        for (i in 0 until minOf(12, entries.length())) {
            val item = entries.optJSONObject(i) ?: continue
            val urls = item.optJSONArray("urls") ?: JSONArray().put(item.optString("urls"))
            val names = (0 until urls.length()).mapNotNull { j ->
                urls.optString(j).takeIf(::validated)
            }
            if (names.isEmpty()) continue
            val isTurn = names.any { it.startsWith("turn") }
            val username = item.optString("username")
            val password = item.optString("credential")
            if (isTurn && (username.isEmpty() || password.isEmpty() ||
                username.length > 120 || password.length > 200)) continue
            servers.add(PeerConnection.IceServer.builder(names)
                .setUsername(username).setPassword(password).createIceServer())
        }
        return servers
    }

    fun load(httpsEndpoint: String, overrides: String = ""):
        List<PeerConnection.IceServer> {
        val servers = stunUrls.map { PeerConnection.IceServer.builder(it).createIceServer() }
            .toMutableList()
        // User-specified TURN is local to the device. It is never uploaded.
        servers.addAll(fromText(overrides))
        val origin = runCatching { RelayDirectory.endpoint(httpsEndpoint) }.getOrNull()
            ?: return servers
        if (origin.protocol != "https") return servers
        val target = URL("https", origin.host, if (origin.port <= 0) 443 else origin.port, "/api/ice")
        val remote = runCatching {
            val conn = target.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 3000
                conn.readTimeout = 4500
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "application/json")
                require(conn.responseCode == 200) { "TURN unavailable" }
                require(conn.contentLengthLong in -1..64000) { "TURN response oversized" }
                val text = conn.inputStream.bufferedReader().use { it.readText().take(64001) }
                require(text.length <= 64000) { "TURN response oversized" }
                parseProvider(text)
            } finally { conn.disconnect() }
        }.getOrDefault(emptyList())
        servers.addAll(remote)
        // Open Relay has historically published shared demonstration TURN
        // credentials. They are BEST EFFORT ONLY and can be revoked or quota-
        // limited at any time. Prefer authenticated Metered /api/ice.
        if (remote.none { it.urls.any { url -> url.startsWith("turn") } } &&
            servers.none { it.urls.any { url -> url.startsWith("turn") } }) {
            servers.add(PeerConnection.IceServer.builder(listOf(
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turn:openrelay.metered.ca:443?transport=tcp"
            )).setUsername("openrelayproject")
              .setPassword("openrelayproject").createIceServer())
        }
        return servers.take(24)
    }

    fun hasTurn(servers: List<PeerConnection.IceServer>): Boolean =
        servers.any { server -> server.urls.any { it.startsWith("turn") } }
}
