# MaizoSNES — validação da interface unificada

**Branch:** `work/android-foundation`. Cada marcação abaixo exige execução real; esta documentação não declara sucesso de testes que não foram realizados.

## Biblioteca e navegação
- [ ] Android pequeno: abas Biblioteca / Multiplayer / Ajustes / Créditos aparecem sem cortes.
- [ ] Tablet/TV (largura >=720dp): sidebar aparece e aciona as quatro telas.
- [ ] Controlador: D-pad/analógico movem foco, A confirma, B volta, L1/R1 navegam abas quando aplicável.
- [ ] Iniciar sem ROM: botão Adicionar jogo abre seletor SAF.
- [ ] Importar ROM própria e exibir título/capa ou placeholder; buscar por título de header e nome.
- [ ] Favoritos/Recentes; grades 2/3/4; Lista horizontal; detalhes; abrir e continuar.
- [ ] Painel de detalhes: patch IPS/BPS, renomear, remover após confirmar, favorito.
- [ ] Rotação de tela, áreas seguras e texto grande.

## Emulação
- [ ] START físico inicia o jogo e **não** abre menu.
- [ ] GUIDE/MODE abre menu quando Android entrega a tecla; touch continua respondendo.
- [ ] Editor touch, skins dos controles, save/load, áudio, shaders e opção de render funcionam.
- [ ] Retornar do menu sem reiniciar/pausar indevidamente o núcleo.

## Multiplayer — não mexer no que funcionava
- [ ] Online criar/entrar/sair, fallback e qualidade de gameplay mantidos.
- [ ] LAN mesmo jogo: conectar e jogar, sem regressão no rollback.
- [ ] LAN ROM diferente: botão Entrar disponível com Aceitar ROM, consentimento **dos dois**, SHA-256 e jogo iniciado.
- [ ] Sem autorização, transferência é negada sem prosseguir; também funciona com IP/porta manual.
- [ ] Alternar aplicativo/tela apagada durante 10min Wi-Fi/4G, coletando logs se falhar.
- [ ] Verificar debug de rede sem falsas indicações de transporte.

## Publicação
- [ ] CI verde e APK com ambas ABIs.
- [ ] Assinatura de Release reprodutível (APK de CI debug não é Release final).
- [ ] Licenças de terceiros e créditos preservados.


## Verificação específica desta revisão visual
- [ ] Relógio, câmera frontal e ícones de status Android não cobrem cabeçalhos/cápsula.
- [ ] Ícones Biblioteca / Multiplayer / Configurações / Créditos aparecem em cápsula no topo no celular e todas as rotas funcionam.
- [ ] Sem ROM: ilustração genérica, Importar acessível, nenhuma arte comercial ou conteúdo externo.
- [ ] Com capa no cache: banner exibe arte ao fundo, com texto legível e PLAY visível.
- [ ] Gamepad: navegar cápsula, lista azul clara, botões de jogo e tela de detalhes.
- [ ] Configurações: todas as oito categorias continuam acessíveis, sem cartões altos desnecessários; sem regressão em filtros/save state.
- [ ] Multiplayer: seção Motor expande/recolhe, UDP/WebRTC e fallback seguem funcionais; atalho de status/log preservado.
- [ ] Ajustar fontes maiores, 360dp de largura, landscape e tablet >=720dp sem sobreposição.

## Convidado sem ROM — regressão de habilitação da UI
- [ ] Com ROM ausente, "Jogo rápido" habilita quando existir uma sala TLS/WSS elegível e a opção de receber ROM estiver ativa.
- [ ] Com ROM ausente e nenhuma sala TLS/WSS elegível, "Jogo rápido" permanece desabilitado (não criar sala vazia).
- [ ] Uma sala com cheats só é elegível quando o usuário autorizou cheats sincronizados.
- [ ] Selecionar sala TLS sem ROM mostra consentimento do host e do convidado, transfere ROM homebrew autorizada, verifica SHA-256 e inicia jogo.
- [ ] Com ROM local, "Jogo rápido" ainda pode criar sala quando nenhuma sala compatível existir.
- [ ] A orientação de Multiplayer distingue claramente "criar sala exige ROM" de "entrar sem ROM é permitido por TLS".

Estes cenários são de **validação física pendente**; o teste de UI por si só não confirma a transferência de ROM nem o transporte UDP/WebRTC.


## Revisão integral de UI/coleção/controles — teste necessário antes da v1.0
- [ ] Com 2, 20, 200 jogos, rolar **toda** a biblioteca: nenhum cartão some atrás do hero/banner.
- [ ] Em retrato e paisagem, alternar Grade 2/3/4, Lista, Fileira e Coleção; fileira desliza horizontalmente.
- [ ] Favoritos e Recentes na Coleção; pesquisar por nome e ordenar Título/Recentes/Arquivo.
- [ ] Trocar entre as abas e voltar à Biblioteca: layout e modo selecionado continuam.
- [ ] Exibir capa de ROM identificada pelo nome do header/arquivo mesmo sem correspondência exata de hash.
- [ ] ROM traduzida/renomeada: catálogo tenta sem tags de região/tradução; não aplica capa de jogo diferente.
- [ ] Verificar cache de capa offline após primeira sincronização; botão Buscar Capas por Nome.
- [ ] Com internet desconectada, falhas de capa não bloqueiam o lançamento dos jogos.
- [ ] Multiplayer: modos Automático / LAN / Offline local com botões de criar/entrar em destaque.
- [ ] Painel técnico de rollback, opções de atraso e ICE aparece ao expandir; nenhum ajuste foi removido.
- [ ] Menu ROMs entre amigos mantém opção de receber e oferecer com confirmação explícita.
- [ ] Paisagem: D-pad inferior esquerdo, ABXY inferior direito, L/R superiores, Start/Select sem cobrir o título.
- [ ] Editor de grupos move D-Pad e ABXY; editor individual move L/R, A/B/X/Y, SELECT/START; confirmar salvamento por jogo.
- [ ] Ajustar tamanho/opacidade de um botão e reiniciar app; somente ele muda; restauração do botão funciona.
- [ ] Testar 10 presets, multitoque com diagonais, START não abre menu.
- [ ] Testar telas 360dp, 480dp, landscape widescreen, notch e fontes ampliadas.
- [ ] Confirmar gameplay e transporte LAN/online seguem com FPS e sincronismo da compilação anterior.


## Validação do layout sem banner e do menu de jogo (candidato 21/10)
- [ ] Abrir Biblioteca no landscape com 1, 20 e 200 títulos: coleção começa no topo, sem banner grande anterior aos jogos.
- [ ] Tocar um cartão de ROM: painel de detalhes com arte grande e JOGAR aparece; voltar preserva busca, filtro, visualização e posição.
- [ ] Na barra **acima** da coleção, abrir **+** e testar Importar ROM/ZIP, Pasta, Procurar ROMs, Buscar capas; nenhum botão perdido.
- [ ] Tocar lupa abre/fecha pesquisa sem esconder cartões; limpar filtros por ícone; filtros favoritos e recentes corretos.
- [ ] Tocar ícone Visualização e trocar Grade 2/3/4/5/Automática, Lista, Fileira, Coleção; ícone Ordenar preserva Título/Recentes/Arquivo.
- [ ] Menu do jogo aberto em landscape: tocar **Vídeo** mostra proporção, filtros e demais opções, com rolagem funcionando.
- [ ] Tocar **Áudio, Controles, Rede, Estados, Cheats, Núcleo, DEV**: a lista abaixo altera imediatamente e volta ao topo.
- [ ] Com nome de ROM longo, cabeçalho usa reticências em vez de impedir visualizar configurações.
- [ ] Em retrato e landscape, janela ocupa área suficiente e oferece rolagem sem transbordar; controle físico e toque funcionam.
- [ ] Escolher PNG/JPEG da galeria para capa, fechar e reabrir app: imagem permanece; Restaurar capa automática funciona.
- [ ] ROM traduzida com capa por nome mantém fundo/arte e não altera hash multiplayer.
- [ ] Configurar d-pad e ABXY no landscape; girar para retrato, editar posições diferentes; girar de volta preserva landscape.
- [ ] No jogo **offline**, sair para Biblioteca; em Detalhes aparece Retomar última partida e o estado é restaurado.
- [ ] **Netplay**: sem save state automático nem restauração individual; verificações SHA-256/consentimento preservadas.
- [ ] Alternar a opção Quick Resume em Estados e confirmar que desativar impede novos autosaves.
- [ ] Com Wi-Fi e 4G: salas online, LAN e fallback TCP funcionam como na build anterior, sem regressões.
