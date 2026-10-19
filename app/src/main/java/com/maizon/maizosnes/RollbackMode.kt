package com.maizon.maizosnes

internal enum class NetcodeMode(val label: String, val info: String) {
    ROLLBACK("Rollback (Recomendado)",
        "GekkoNet UDP: entrada instantânea e previsão de quadros. " +
        "Se indisponível, alterna para TCP lockstep."),
    LOCKSTEP("Lockstep (Compatibilidade)",
        "Protocolo TCP original, compatível com IP manual, LAN e relay.")
}

internal data class RollbackBenchmark(val bytes: Int, val saveMs: Double,
    val loadMs: Double, val udpBound: Boolean)

internal object RollbackFeasibility {
    const val MAX_STATE_BYTES = 16 * 1024 * 1024
    /** CPU emulation must keep room for rollback resimulation inside 16.7 ms. */
    fun rejection(stats: RollbackBenchmark): String? = when {
        stats.bytes <= 32 || stats.bytes > MAX_STATE_BYTES ->
            "Estado muito grande/indisponível para cache de rollback"
        !stats.udpBound -> "Porta UDP indisponível"
        !stats.saveMs.isFinite() || !stats.loadMs.isFinite() ->
            "Medição de save/load inválida"
        stats.saveMs + stats.loadMs > 10.0 ->
            "Save/load custa %.1f ms; acima do limite de 10 ms"
                .format(java.util.Locale.US, stats.saveMs + stats.loadMs)
        else -> null
    }
}
