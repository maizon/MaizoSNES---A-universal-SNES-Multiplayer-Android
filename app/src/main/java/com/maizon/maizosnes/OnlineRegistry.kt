package com.maizon.maizosnes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/** A room hosted on a specific operator-approved relay.
 * No third-party RetroArch/PPSSPP server is silently reused.
 */
internal data class PublicRelay(
    val name: String,
    val region: String,
    val endpoint: String
)
internal data class AutoRoom(
    val id: String,
    val game: String,
    val serverName: String,
    val region: String,
    val endpoint: String,
    val latencyMs: Long,
    val romHash: String = "",
    val cheats: Boolean = false,
    val tls: Boolean = false,
    val wss: Boolean = false
)
internal data class AutoServerStatus(
    val relay: PublicRelay,
    val latencyMs: Long,
    val roomCount: Int
)
internal data class AutoSearch(
    val rooms: List<AutoRoom>,
    val servers: List<AutoServerStatus>,
    val error: String?
)

/** Downloads a FIRST-PARTY manifest over HTTPS and checks up to 8 relays.
 *
 * There is intentionally NO fake hardcoded public server. Once an operator
 * deploys relays and adds their URLs to server/registry.json, existing APKs
 * discover them automatically on their next search without typing an IP.
 */
internal enum class RelayProbe { WSS, TLS_TCP, TCP }
internal data class RelayScanAttempt(
    val status: AutoServerStatus?,
    val rooms: List<AutoRoom>,
    val failure: String?
)

internal object OnlineRegistry {
    /** A WSS directory does not depend on its legacy TCP proxy. */
    internal fun preferredProbe(listing: RelayListing): RelayProbe = when {
        listing.wss -> RelayProbe.WSS
        listing.tls -> RelayProbe.TLS_TCP
        else -> RelayProbe.TCP
    }

    private const val MANIFEST_BASE =
        "https://raw.githubusercontent.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/"
    private const val MAIN_MANIFEST = MANIFEST_BASE +
        "refs/heads/main/server/registry.json"
    private const val PREVIEW_MANIFEST = MANIFEST_BASE +
        "refs/heads/work/android-foundation/server/registry.json"

    internal fun parseRegistry(json: String): List<PublicRelay> {
        require(json.length <= 20000) { "Registro de servidores muito grande." }
        val data = JSONObject(json)
        require(data.optInt("version") == 1) { "Versão de registro incompatível." }
        val list = data.getJSONArray("servers")
        require(list.length() <= 8) { "Registro excede 8 servidores." }
        val output = mutableListOf<PublicRelay>()
        val used = HashSet<String>()
        for (index in 0 until list.length()) {
            val item = list.getJSONObject(index)
            if (!item.optBoolean("enabled", true)) continue
            val name = item.optString("name").trim().take(36)
            val region = item.optString("region").trim().take(25)
            val endpoint = item.optString("url").trim()
            if (name.isEmpty() || endpoint.isEmpty()) continue
            val url = URI(endpoint)
            // The registry comes from our public repo. Operators must configure
            // HTTPS to protect metadata against interception/replacement.
            require(url.scheme?.lowercase(Locale.US) == "https" &&
                !url.host.isNullOrBlank() && url.userInfo == null &&
                url.rawQuery == null && url.rawFragment == null &&
                (url.rawPath.isNullOrEmpty() || url.rawPath == "/")) {
                "Servidor público exige URL base HTTPS sem credenciais."
            }
            if (used.add(url.normalize().toString())) {
                output.add(PublicRelay(name, region.ifEmpty { "Global" }, endpoint))
            }
        }
        return output
    }

    private fun downloadRegistry(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500
            readTimeout = 4500
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MaizoSNES-Online/1")
        }
        return try {
            require(connection.responseCode == 200) {
                "Manifesto de servidores indisponível (HTTP ${connection.responseCode})."
            }
            val bytes = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 20000) { "Manifesto excessivo." }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    internal fun loadRegistered(): List<PublicRelay> {
        var lastError: Exception? = null
        var manifestLoaded = false
        for (source in listOf(MAIN_MANIFEST, PREVIEW_MANIFEST)) {
            try {
                val relays = parseRegistry(downloadRegistry(source))
                manifestLoaded = true
                if (relays.isNotEmpty()) return relays
            } catch (e: Exception) {
                lastError = e
            }
        }
        // An empty first-party registry is an expected initial state: the
        // project owner must first deploy and register real relay servers.
        if (manifestLoaded) return emptyList()
        if (lastError != null) throw lastError
        return emptyList()
    }

    suspend fun scan(): AutoSearch = coroutineScope {
        val manifest = runCatching {
            withContext(Dispatchers.IO) { loadRegistered() }
        }.getOrElse { error ->
            return@coroutineScope AutoSearch(emptyList(), emptyList(),
                "Não foi possível consultar o registro: " +
                    (error.message ?: "falha de rede"))
        }
        if (manifest.isEmpty()) {
            return@coroutineScope AutoSearch(emptyList(), emptyList(),
                "Nenhum servidor público MaizoSNES foi publicado ainda. " +
                    "O multiplayer LAN e o servidor manual continuam disponíveis.")
        }
        val attempts = manifest.map { server ->
            async(Dispatchers.IO) {
                runCatching {
                    val start = System.nanoTime()
                    val listing = RelayDirectory.list(server.endpoint)
                    // Probe the SAME transport used for HOST/JOIN. A WSS-only
                    // Railway deployment may not expose a public TCP proxy.
                    when (preferredProbe(listing)) {
                        RelayProbe.WSS -> WssRelaySocket.connect(server.endpoint).use { }
                        RelayProbe.TLS_TCP, RelayProbe.TCP -> {
                            java.net.Socket().use { socket ->
                                socket.connect(java.net.InetSocketAddress(
                                    listing.serverHost, listing.relayPort), 2200)
                                socket.soTimeout = 2500
                                if (listing.tls) {
                                    val wrapped =
                                        (javax.net.ssl.SSLSocketFactory.getDefault()
                                            as javax.net.ssl.SSLSocketFactory)
                                            .createSocket(socket, listing.serverHost,
                                                listing.relayPort, false) as javax.net.ssl.SSLSocket
                                    wrapped.use { ssl ->
                                        ssl.sslParameters = ssl.sslParameters.apply {
                                            endpointIdentificationAlgorithm = "HTTPS"
                                        }
                                        ssl.startHandshake()
                                    }
                                }
                            }
                        }
                    }
                    val elapsed = ((System.nanoTime() - start) / 1_000_000L)
                        .coerceIn(1L, 120000L)
                    val rooms = listing.rooms.map { room ->
                        AutoRoom(room.id, room.game, server.name, server.region,
                            server.endpoint, elapsed, room.romHash, room.cheats, room.tls, room.wss)
                    }
                    RelayScanAttempt(AutoServerStatus(server, elapsed, rooms.size),
                        rooms, null)
                }.getOrElse { error ->
                    RelayScanAttempt(null, emptyList(),
                        "${server.name}: " +
                            (error.message ?: error.javaClass.simpleName).take(130))
                }
            }
        }.awaitAll()
        val status = attempts.mapNotNull { it.status }.sortedBy { it.latencyMs }
        val rooms = attempts.flatMap { it.rooms }.sortedWith(
            compareBy<AutoRoom> { it.latencyMs }.thenBy { it.game.lowercase() })
        val failures = attempts.mapNotNull { it.failure }
        val problem = when {
            status.isEmpty() -> "Servidores públicos inacessíveis. " +
                failures.take(3).joinToString(" | ") + ". LAN/IP manual continuam disponíveis."
            failures.isNotEmpty() -> "${rooms.size} sala(s) disponíveis; " +
                "falha parcial: " + failures.take(2).joinToString(" | ")
            else -> null
        }
        AutoSearch(rooms, status, problem)
    }
}
