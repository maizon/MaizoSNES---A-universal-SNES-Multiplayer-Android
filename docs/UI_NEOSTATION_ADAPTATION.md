# Adaptação visual MaizoSNES • referência NeoStation Frontend

Referência estudada: [misobadev/neostation-frontend](https://github.com/misobadev/neostation-frontend), incluindo o arquivo ZIP fornecido pelo usuário em 21/10/2026. O NeoStation é um frontend Flutter de vários sistemas; o MaizoSNES é um emulador SNES Android nativo, com multiplayer próprio.

## Escopo desta integração em um commit

| Área | Experiência visual no MaizoSNES |
| --- | --- |
| Identidade visual | Novo tema escuro com superfícies neutras, violeta, rosa e acentos verde-água, inspirado no esquema dark do NeoStation. |
| Navegação | Barra inferior nos celulares e sidebar permanente em telas de largura >=720dp, com ícones, foco visual e identidade MaizoSNES. |
| Biblioteca | Cabeçalho e contagem, jogo em destaque com capa do cache ou placeholder, Jogar/Continuar, importar, buscar, filtros Todos/Favoritos/Recentes. |
| Jogos | Grade 2/3/4 colunas com capas, cartões redesenhados, lista horizontal real, indicador de jogo ativo e painel de detalhes com região e ações preservadas. |
| Multiplayer | Cabeçalho e painel de estado remodelados, seleção de modalidade mais visível; permanecem criar/entrar/refresh de salas LAN e online, ICE, debug e consentimentos. |
| Ajustes | Página com cabeçalho e categorias temáticas, chips selecionados e cartões; nenhuma opção do Snes9x, controles ou save states foi removida. |
| Jogo | Barra sobreposta e painel de pausa/edição remodelados, sem alterar SurfaceView, mapeamentos de botões, START ou sincronização. |
| Créditos | Cartão do criador Maicon Nascimento, Instagram e agradecimentos detalhados. |
| Status | Mensagens de conexão, debug e prompts de ROM mantidos para evitar esconder erros reais. |

## Exatamente o que não foi portado

O objetivo aqui é **adaptação visual e funcional à arquitetura do MaizoSNES**, não transplante literal de cada tela e recurso de outro aplicativo. NeoStation possui funcionalidades externas ao escopo SNES (emuladores de outros sistemas, RetroAchievements completo, RomM, sistemas de scraping, músicas, coleções inteligentes, etc.). Elas não foram incorporadas nem anunciadas como implementadas.

**Nenhum código Dart, arquivos de imagem, fontes, logotipos nem outros recursos de NeoStation foram incorporados.** As formas, cores e componentes acima foram implementados independentemente em Jetpack Compose. NeoStation é GPLv3; Snes9x possui condições próprias não comerciais. Antes de copiar qualquer arquivo de terceiros é necessária análise de licenças. Uso das ideias de UI não implica endosso do projeto original.

## Política de regressão

Esta rodada é exclusiva de UI: não alterar `native-lib.cpp`, `NetplaySession.kt`, `LanDiscovery.kt`, WebRTC, Railway, SHA-256, SAF ou autorização bilateral de ROM. O menu do controle GUIDE/MODE continua disponível; START permanece START dentro do SNES.

## Gate real de aprovação

1. GitHub Actions verde: build Gradle, testes unitários Android/servidor, lib nativa ARMv7/ARM64 e APK.
2. Navegar com toque e gamepad nas quatro abas e no diálogo de jogo; importar ROM; buscar; escolher grade/lista.
3. Entrar no jogo, testar barra e menu, pausar, save/load state, vídeo/áudio e touch, sem reatribuir START.
4. Dois dispositivos: LAN e online previamente aprovados; testar novamente criar/entrar/receber ROM com permissão e jogar sem regressão.
5. Verificar telas pequenas, 720dp+, paisagem e fontes grandes. Resultado físico depende de logs e testes do usuário, não do CI.

Não publicar como "v1.0 definitiva validada" somente pelo CI.


## Correção de fidelidade visual após capturas no dispositivo (21/10/2026)

A versão inicial não reproduzia o padrão característico do NeoStation. As capturas enviadas pelo usuário mostraram excesso de cartões Material, navegação inferior e cabeçalhos sob os ícones Android. A revisão corrige a organização visual (sem trazer Flutter): **navegação em cápsula superior**, painel/hero de jogo com arte de capa e gradiente, paleta focada em azul claro e PLAY verde, cartões translúcidos, seleção de linhas com preenchimento azul, motor de multiplayer recolhível e status bar segura. Sem capas próprias, a biblioteca mostra arte vetorial original genérica, por isso não terá o fundo de um jogo específico até uma ROM/capa estar disponível.

A referência NeoStation para composição de biblioteca inclui as capturas em `docs/screenshots/list-view-size/` do ZIP original, vistas para referência visual apenas. Não adicionar capturas/artwork do projeto GPLv3 ou de jogos comerciais aos recursos Android; não distribuir mídia sem autorização.

**Conferência física pendente:** telas pequenas, fontes ampliadas, área status/navigation e comportamento real do gamepad; capturas do usuário não equivalem a validação da nova build.


## Biblioteca completa e UX multiplayer — 21/10/2026

A implementação tem agora modos Grade 2/3/4, Lista vertical, Fileira horizontal e Coleção com seções Favoritos/Recentes/Todos, com rolagem única que inclui o banner em vez de prender a coleção a um espaço residual. Capas por título de SNES/arquivo normalizado podem ser carregadas opcionalmente do índice público libretro e mantidas em cache; não são recursos gráficos NeoStation nem jogos empacotados.

A tela de redes prioriza salas antes de detalhes técnicos, mantém edição de consentimento e acesso aos perfis de rede. O editor de controles foi unificado para todos os layouts e salva escala/opacidade por botão. Ainda é **adaptação em Kotlin** de princípios visuais, não execução do frontend Flutter original.
