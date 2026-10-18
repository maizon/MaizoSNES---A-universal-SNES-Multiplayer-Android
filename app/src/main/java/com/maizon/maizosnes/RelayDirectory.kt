package com.maizon.maizosnes

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Directory + outbound TCP relay, hosted by the user on a reachable server.
 * This deliberately does NOT claim direct P2P, NAT punching, or encrypted TCP.
 */
internal data class InternetRoom(val id: String, val game: String, val createdAt: Long)
internal data class RelayListing(val serverHost: String, val relayPort: Int, val rooms: List<InternetRoom>)

internal object RelayDirectory {
    fun list(address: String): RelayListing {
        val raw = address.trim().take(300)
        require(raw.isNotBlank()) { "Informe o IP ou URL do servidor." }
        val normalized = if (raw.startsWith("http://", true) ||
            raw.startsWith("https://", true)) raw else "http://$raw"
        val parsed = URI(normalized)
        require(parsed.scheme.lowercase() in setOf("http", "https")) {
            "Use URL http:// ou https://."
        }
        require(!parsed.host.isNullOrBlank() && parsed.userInfo == null) {
            "Servidor inválido."
        }
        val base = "${parsed.scheme}://${parsed.rawAuthority}"
        val url = URL("$base/api/rooms")
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
        val values = json.getJSONArray("rooms")
        val rooms = ArrayList<InternetRoom>()
        for (index in 0 until minOf(values.length(), 100)) {
            val item = values.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (!id.matches(Regex("[a-f0-9]{12,24}"))) continue
            rooms.add(InternetRoom(id, item.optString("game").take(75),
                item.optLong("createdAt")))
        }
        return RelayListing(parsed.host, port, rooms)
    }
}
