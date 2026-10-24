package com.maizon.maizosnes

/** Presentation-only messages for the local, user-initiated NES core check. */
internal object NesDiagnosticReport {
    fun describe(code: Int): String = when (code) {
        0 -> "NES: 2 reexecuções produziram estados idênticos. " +
            "Teste local aprovado; multiplayer real ainda precisa de validação."
        -1 -> "Teste disponível somente com NES carregado, pausado e sem multiplayer."
        -2 -> "Estado NES ausente ou maior que 2 MiB; rollback requer atenção."
        -3 -> "FCEUmm não conseguiu salvar o estado de teste."
        -4 -> "FCEUmm falhou ao restaurar/executar o teste; não usar rollback sem diagnóstico."
        -5 -> "NES não reproduziu estados idênticos; revise o core antes do rollback."
        else -> "Teste NES indisponível (código $code)."
    }
}
