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
