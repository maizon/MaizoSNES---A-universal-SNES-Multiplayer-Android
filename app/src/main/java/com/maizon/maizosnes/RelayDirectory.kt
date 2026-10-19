package com.maizon.maizosnes

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Directory + outbound TCP relay, hosted by the user on a reachable server.
 * This deliberately does NOT claim direct P2P, NAT punching, or encrypted TCP.
 */
internal data class InternetRoom(
    val id: String,
    val game: String,
    val createdAt: Long,
    val romHash: String = "",
    val cheats: Boolean = false,
    val tls: Boolean = false,
    val wss: Boolean = false
)
internal data class RelayListing(
    val serverHost: String, val relayPort: Int,
    val rooms: List<InternetRoom>, val tls: Boolean = false,
    val wss: Boolean = false
)

internal object RelayDirectory {
    /** Bare IP/hostname works without typing http:// or the default :8787. */
    internal fun endpoint(address: String): URL {
        val raw = address.trim().take(300)
        require(raw.isNotBlank()) { "Informe o IP ou URL do servidor." }
        val normalized = if (raw.startsWith("http://", true) ||
            raw.startsWith("https://", true)) raw else "http://$raw"
        val parsed = URI(normalized)
        require(parsed.scheme.lowercase() in setOf("http", "https")) {
            "Use URL http:// ou https://."
        }
        require(!parsed.host.isNullOrBlank() && parsed.userInfo == null &&
            parsed.rawQuery == null && parsed.rawFragment == null &&
            (parsed.rawPath.isNullOrEmpty() || parsed.rawPath == "/")) {
            "Informe somente o IP, domínio ou URL base do servidor."
        }
        // A plain IP is intentionally mapped to port 8787. HTTPS without
        // explicit port may be terminated by the owner's reverse proxy on 443.
        val defaultPort = if (parsed.scheme.equals("https", true)) -1 else 8787
        val effectivePort = if (parsed.port == -1) defaultPort else parsed.port
        require(effectivePort == -1 || effectivePort in 1..65535) {
            "Porta HTTP inválida."
        }
        val authority = if (parsed.port == -1 && effectivePort != -1)
            "${parsed.rawAuthority}:$effectivePort" else parsed.rawAuthority
        return URL("${parsed.scheme}://$authority/api/rooms")
    }

    /** The public TCP proxy may be on a different hostname than the HTTPS API.
     * Reject URL, path, user-info and port injection in relayHost metadata.
     */
    internal fun validateRelayHost(directoryHost: String, advertisedHost: String): String {
        val host = advertisedHost.trim()
        if (host.isBlank()) return directoryHost
        require(host.length <= 253 && host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]"))) {
            "Endereço TCP anunciado pelo servidor é inválido."
        }
        return host
    }

    fun list(address: String): RelayListing {
        val url = endpoint(address)
        val parsed = URI(url.toExternalForm())
        val request = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6500
            readTimeout = 6500
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
        }
        val json = try {
            require(request.responseCode == 200) {
                "Servidor HTTP respondeu ${request.responseCode}."
            }
            val data = request.inputStream.use { input ->
                val bytes = ByteArray(65537)
                var read = 0
                while (read < bytes.size) {
                    val count = input.read(bytes, read, bytes.size - read)
                    if (count < 0) break
                    read += count
                }
                require(read <= 65536) { "Resposta muito grande." }
                String(bytes, 0, read, Charsets.UTF_8)
            }
            JSONObject(data)
        } finally { request.disconnect() }
        require(json.optString("protocol") == "maizo-relay-v1") {
            "Servidor não suporta MaizoSNES Relay v1."
        }
        val port = json.optInt("relayPort", -1)
        require(port in 1..65535) { "Porta TCP de relay inválida." }
        val relayTls = json.optBoolean("relayTls", false)
        val wss = json.optBoolean("websocketRelay", false) &&
            url.protocol.equals("https", true)
        require(!relayTls || url.protocol.equals("https", true)) {
            "Relay TLS requer diretório HTTPS para proteger endereço e porta."
        }
        val values = json.getJSONArray("rooms")
        val rooms = ArrayList<InternetRoom>()
        for (index in 0 until minOf(values.length(), 100)) {
            val item = values.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (!id.matches(Regex("[a-f0-9]{12,24}"))) continue
            val romHash = item.optString("romHash").lowercase()
            rooms.add(InternetRoom(id, item.optString("game").take(75),
                item.optLong("createdAt"),
                romHash.takeIf { it.matches(Regex("[a-f0-9]{16}")) } ?: "",
                item.optBoolean("cheats", false), relayTls,
                wss && item.optBoolean("websocketRelay", false)))
        }
        val relayHost = validateRelayHost(parsed.host, json.optString("relayHost", ""))
        return RelayListing(relayHost, port, rooms, relayTls, wss)
    }
}
