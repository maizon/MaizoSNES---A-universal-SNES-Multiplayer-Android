package com.maizon.maizosnes

/**
 * Mode selection changes connection discovery/timeouts and the UI profile,
 * NOT the established MZNP v2 wire protocol. Save-state/ROM progress is intact.
 */
internal enum class NetworkMode(val label: String, val hint: String) {
    LOCAL("Offline local", "Dois controles no mesmo Android, sem rede"),
    LAN_AUTO("LAN automática", "Descobre salas na mesma rede Wi-Fi"),
    LAN_MANUAL("LAN por IP", "Conecta a um IPv4 e porta na mesma rede"),
    INTERNET("Internet / VPN", "Conexão manual pela internet ou VPN privada; requer endereço alcançável")
}

internal data class NetworkMetrics(
    val frame: Long,
    val peerWaitMs: Double,
    val maxPeerWaitMs: Double,
    val effectiveFps: Double,
    val stalledFrames: Long
)

internal enum class VideoFilter(val label: String, val nativeId: Int, val description: String) {
    ORIGINAL("Original (rápido)", 0, "RGB565 direto, sem processamento extra"),
    SCANLINES("Scanlines leves", 1, "Escurece linhas alternadas, processamento muito baixo"),
    SOFT("Suavização leve", 2, "Mistura pixels horizontalmente sem GPU"),
    CRT_SOFT("CRT leve + scanlines", 3, "Combinação de suavização e scanlines; opcional")
}
