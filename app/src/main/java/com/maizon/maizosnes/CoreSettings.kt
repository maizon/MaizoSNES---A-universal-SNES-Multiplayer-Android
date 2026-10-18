package com.maizon.maizosnes

internal data class CoreOptionDefinition(
    val key: String,
    val title: String,
    val description: String,
    val category: String,
    val defaultValue: String,
    val values: List<String>
)

internal object CoreOptionDefinitions {
    val all = listOf(
        CoreOptionDefinition("snes9x_aspect", "Proporção do núcleo", "Geometria de vídeo informada pelo Snes9x.", "Vídeo", "4:3", listOf("4:3", "4:3 scaled", "uncorrected", "auto", "ntsc", "pal")),
        CoreOptionDefinition("snes9x_overscan", "Corte de overscan", "Corta bordas normalmente escondidas em televisores antigos.", "Vídeo", "enabled", listOf("enabled", "disabled", "auto")),
        CoreOptionDefinition("snes9x_hires_blend", "Mistura Hi-Res", "Combina pixels em modos de alta resolução usados por alguns jogos.", "Vídeo", "disabled", listOf("disabled", "merge", "blur")),
        CoreOptionDefinition("snes9x_blargg", "Filtro NTSC Blargg", "Filtros de vídeo do núcleo; podem aumentar o custo de renderização.", "Vídeo", "disabled", listOf("disabled", "monochrome", "rf", "composite", "s-video", "rgb")),
        CoreOptionDefinition("snes9x_layer_1", "Exibir camada de fundo 1", "Ativa ou oculta a primeira camada gráfica.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_layer_2", "Exibir camada de fundo 2", "Ativa ou oculta a segunda camada gráfica.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_layer_3", "Exibir camada de fundo 3", "Ativa ou oculta a terceira camada gráfica.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_layer_4", "Exibir camada de fundo 4", "Ativa ou oculta a quarta camada gráfica.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_layer_5", "Exibir sprites", "Ativa ou oculta a camada de sprites.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_gfx_clip", "Janelas gráficas", "Ativa os efeitos de recorte/janela gráficos.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_gfx_transp", "Efeitos de transparência", "Ativa transparências e efeitos relacionados.", "Vídeo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_audio_interpolation", "Interpolação de áudio", "Escolhe o filtro do sintetizador de áudio do núcleo.", "Áudio", "gaussian", listOf("gaussian", "cubic", "sinc", "none", "linear")),
        CoreOptionDefinition("snes9x_sndchan_1", "Canal de som 1", "Liga ou desliga o canal de áudio 1.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_2", "Canal de som 2", "Liga ou desliga o canal de áudio 2.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_3", "Canal de som 3", "Liga ou desliga o canal de áudio 3.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_4", "Canal de som 4", "Liga ou desliga o canal de áudio 4.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_5", "Canal de som 5", "Liga ou desliga o canal de áudio 5.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_6", "Canal de som 6", "Liga ou desliga o canal de áudio 6.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_7", "Canal de som 7", "Liga ou desliga o canal de áudio 7.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_sndchan_8", "Canal de som 8", "Liga ou desliga o canal de áudio 8.", "Áudio", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_region", "Região", "Auto detecta NTSC/PAL; forçar uma região pode mudar a velocidade do jogo.", "Núcleo", "auto", listOf("auto", "ntsc", "pal")),
        CoreOptionDefinition("snes9x_overclock_cycles", "Reduzir lentidão (hack)", "Hack de overclock do CPU SNES; pode introduzir erros ou travamentos.", "Núcleo", "disabled", listOf("disabled", "light", "compatible", "max")),
        CoreOptionDefinition("snes9x_overclock_superfx", "Frequência SuperFX", "Afeta jogos que usam SuperFX; valores altos podem causar problemas.", "Núcleo", "100%", listOf("50%", "60%", "70%", "80%", "90%", "100%", "150%", "200%", "250%", "300%", "350%", "400%", "450%", "500%")),
        CoreOptionDefinition("snes9x_up_down_allowed", "Permitir direções opostas", "Permite esquerda+direita ou cima+baixo simultaneamente.", "Núcleo", "disabled", listOf("disabled", "enabled")),
        CoreOptionDefinition("snes9x_reduce_sprite_flicker", "Reduzir cintilação de sprites", "Hack que desenha mais sprites por linha; pode alterar compatibilidade.", "Núcleo", "disabled", listOf("disabled", "enabled")),
        CoreOptionDefinition("snes9x_randomize_memory", "Randomizar memória ao iniciar", "Opção experimental, não recomendada para partidas sincronizadas.", "Núcleo", "disabled", listOf("disabled", "enabled")),
        CoreOptionDefinition("snes9x_block_invalid_vram_access", "Bloquear acesso VRAM inválido", "Algumas ROM hacks exigem desativar esta proteção.", "Núcleo", "enabled", listOf("enabled", "disabled")),
        CoreOptionDefinition("snes9x_echo_buffer_hack", "Hack de buffer de eco", "Hack específico para patches antigos de música; normalmente deve ficar desligado.", "Núcleo", "disabled", listOf("disabled", "enabled"))
    )
}
