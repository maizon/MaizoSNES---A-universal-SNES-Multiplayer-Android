package com.maizon.maizosnes

/**
 * Mode selection changes connection discovery/timeouts and the UI profile,
 * MZNP v5 uses negotiated input pipelining. Save-state/ROM progress is intact.
 */
internal enum class NetworkMode(val label: String, val hint: String) {
    AUTO("Automático (recomendado)", "Encontra LAN e servidores MaizoSNES online sem inserir IP ou URL"),
    LOCAL("Offline local", "Dois controles no mesmo Android, sem rede"),
    LAN_AUTO("LAN automática", "Descobre salas na mesma rede Wi-Fi"),
    LAN_MANUAL("LAN por IP", "Conecta a um IPv4 e porta na mesma rede"),
    INTERNET("Internet / VPN", "Conexão manual pela internet ou VPN privada; requer endereço alcançável"),
    RELAY("Servidor de salas (NAT / CGNAT)", "Digite apenas o IP ou URL do servidor; descubra e entre em salas online")
}

/**
 * Trade controller reaction time against jitter resilience. The host chooses
 * the common buffer; the client never uses an independent frame lead.
 */
internal enum class NetplayLatencyMode(val label: String, val hint: String) {
    RESPONSIVE("Resposta rápida", "Menor atraso nos botões; exige rede estável, pode engasgar"),
    BALANCED("Equilibrado (recomendado)", "RTT detectado automaticamente com folga leve para jitter"),
    STABLE("Conexão instável", "Mais frames de buffer contra jitter, com maior atraso nos botões")
}

internal data class NetworkMetrics(
    val frame: Long,
    val peerWaitMs: Double,
    val maxPeerWaitMs: Double,
    val effectiveFps: Double,
    val stalledFrames: Long,
    val inputDelayFrames: Int = 0,
    val sampledRttMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val rollbackCount: Long = 0,
    val rollback: Boolean = false,
    val webRtc: Boolean = false,
    val iceState: String = "N/D",
    val turnUsed: String = "N/D",
    val natType: String = "N/D",
    val saveLoadMs: Double = 0.0,
    val maxFrameStallMs: Double = 0.0,
    val presentationStride: Int = 1,
    val limitedSaving: Boolean = false
)

/** Runtime truth for the settings/in-game connection diagnostics. */
internal data class NetworkDebugState(
    val active: Boolean = false,
    val mode: String = "Sem partida ativa",
    val transport: String = "—",
    val peer: String = "—",
    val rttMin: Double? = null, val rttAvg: Double? = null,
    val rttMax: Double? = null, val jitterMs: Double? = null,
    val packetLoss: String = "N/D (não medido)",
    val rollbacksPerSec: Long = 0, val totalRollbacks: Long = 0,
    val delay: Int? = null, val desyncs: Int = 0,
    val frame: Long = 0, val state: String = "Aguardando sessão",
    val iceState: String = "N/D", val natType: String = "N/D",
    val turnUsed: String = "N/D",
    val saveLoadMs: Double? = null,
    val peakFrameStallMs: Double = 0.0,
    val presentationStride: Int = 1,
    val limitedSaving: Boolean = false
)
internal enum class VideoFilter(val label: String, val nativeId: Int, val description: String) {
    ORIGINAL("Original (rápido)", 0, "RGB565 direto, sem processamento extra"),
    SCANLINES("Scanlines leves", 1, "Escurece linhas alternadas, processamento muito baixo"),
    SOFT("Suavização leve", 2, "Mistura pixels horizontalmente sem GPU"),
    CRT_SOFT("CRT leve + scanlines", 3, "Combinação de suavização e scanlines; opcional"),
    LCD_GRID("LCD / grade", 4, "Grade leve sobre os pixels, CPU"),
    GRAYSCALE("Preto e branco", 5, "Escala de cinza"),
    RETRO_GREEN("Monitor verde", 6, "Estilo monitor monocromático verde"),
    SHARPEN("Nitidez / sharpen", 7, "Aumenta contraste das bordas dos pixels"),
    RGB_TRIAD("CRT RGB triad", 8, "Simula faixas de fósforo R/G/B, CPU")
}

/** Native framebuffer versus experimental OpenGL ES 2 texture/pixel shaders. */
internal enum class RenderBackend(val label: String, val hint: String) {
    CPU("CPU RGB565 (estável)", "Renderização nativa direta, menor consumo"),
    GLES("OpenGL ES 2 (GPU / Mali)", "Escala por GPU com shaders; experimental em aparelhos reais")
}

internal enum class GpuShader(val label: String, val glId: Int, val hint: String) {
    NEAREST("Nearest (pixels nítidos)", 0, "Textura RGB565 com amostragem nearest"),
    LINEAR("Bilinear GPU", 1, "Interpolação linear de escala pelo hardware"),
    SMOOTH("Suavização 4 vizinhos", 2, "Suaviza artefatos, sem ser FXAA completo"),
    CRT("CRT curvo / scanlines", 3, "Curvatura leve, vignette e scanlines na GPU"),
    LCD("LCD grid", 4, "Linhas leves, sem passagens adicionais"),
    SHARP("Nitidez / sharpen GPU", 5, "Realça bordas usando 2 amostras adjacentes"),
    GRAY("Preto e branco GPU", 6, "Escala de cinza por shader"),
    SATURATE("Cores vivas GPU", 7, "Saturação 1.3x por shader")
}
