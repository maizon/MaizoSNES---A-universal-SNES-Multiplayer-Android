# MaizoSNES — checklist para APK experimental

**Ainda não executado em dois celulares nesta revisão.** Build CI ou unit test não substituem teste manual.

## App e biblioteca

- [ ] Abrir sem crash (Android 8, 11, 14, 16).
- [ ] Importar uma ROM própria .sfc e .smc; ZIP com exatamente uma ROM.
- [ ] Rejeitar arquivos inválidos e ZIP com duas ROMs; limitar ROM a 64 MiB.
- [ ] Fechar/reabrir e iniciar jogo da biblioteca sem picker.
- [ ] Renomear ROM e trocar de ROM mantendo estados corretos.

## Vídeo, controls e áudio

- [ ] Gameplay em tela landscape dedicada, sem bordas do sistema; saída restaura barras.
- [ ] 4:3, Pixel Perfect, 16:9, stretch, fill/crop em jogos 256×224 e Hi-Res.
- [ ] Joystick D-pad deslizante mantém cima, esquerda/cima diagonal, baixo/direita.
- [ ] Dois dedos: direcional+ABXY simultâneos; SELECT/START/L/R.
- [ ] EDITAR arrasta direcional/botões, SALVAR e ajustes persistem por perfil.
- [ ] Testar escala 0.70–1.45 e opacidade 20–90%.
- [ ] Gamepad Bluetooth/USB e hotplug, sem botão preso após pausa.
- [ ] AudioTrack 30 min sem estalo, underrun ou atraso; validar mute/volume.

## Multiplayer offline local

- [ ] 1 gamepad + touchscreen controla P1 e P2 separadamente.
- [ ] 2 gamepads físicos controlam P1/P2 sem mistura de entradas.
- [ ] 2 jogadores em ROM oficialmente compatível (ex.: jogo cooperativo).

## Multiplayer LAN e internet por IP

- [ ] Mesmo APK + SHA ROM + opções em dois aparelhos, conexão pelo mesmo Wi-Fi.
- [ ] Host P1 / convidado P2 sem trocar porta; inputs corretos em frames consecutivos.
- [ ] ROM diferente e core option diferente mostram erro específico antes de jogar.
- [ ] Hash periódico não acusa divergência falsa em jogo simples.
- [ ] Encerrar sessão, sair do app, trocar rede Wi-Fi: fecha socket e não trava UI.
- [ ] Gameplays de 10–30 min com vários jogos NTSC e PAL sem dessíncronia.
- [ ] Medir input-to-display e RTT LAN para comparar com emulação local.
- [ ] Teste internet via VPN privada, com latência/perda/jitter; atenção à ausência de criptografia na camada do app.
- [ ] Teste em dois aparelhos ARM64 de SoCs diferentes (não inferir determinismo pela CI).

Registre modelo, Android, versão do APK, hash SHA-256 do APK, resultado e trecho do logcat sem ROM ou dados pessoais.

## Cheats automáticos
- [ ] ROM nomeada e ROM renomeada (título interno SNES) localizam cheats por nome, sem cadastrar manualmente.
- [ ] Sem internet na primeira consulta, erro controlado. Com internet, baixar catálogo; depois offline usar cache.
- [ ] Filtrar cheats por descrição, ativar/desativar um e vários; efeito no jogo não causa crash.
- [ ] Ao mudar de jogo, cheats do anterior não permanecem ativos; escolhas por hash da ROM são preservadas.
- [ ] Durante netplay, cheats não podem ser ativados, e opções anteriores são restauradas após encerrar.
- [ ] Um código inválido não é enviado ao núcleo C++ e nenhuma string longa é copiada inseguramente.

## Descoberta e importação em rede
- [ ] Na aba LAN, busca automática encontra host no mesmo Wi-Fi sem internet; repetir com BUSCAR.
- [ ] Selecionar sala preenche/conecta ao IP e porta anunciados.
- [ ] Conectar manualmente por IP e porta permanece disponível em redes com broadcast bloqueado.
- [ ] Cliente sem ROM: com consentimentos host+cliente, em rede privada e conteúdo homebrew redistribuível, recebe até 64 MiB, confere SHA, salva na pasta interna e carrega automaticamente.
- [ ] Sem consentimento de uma das partes, nenhuma ROM é enviada.
- [ ] Conexão pública/loopback e ROM incorreta não recebem compartilhamento.
- [ ] Desligar Wi-Fi/forçar desconexão não deixa socket/trava ou envio parcial recuperável.
- [ ] Checar que o botão de menu fica em círculo pequeno translúcido ao recolher e não bloqueia gameplay.

## Consolidação final — ensaios específicos

- [ ] Testar separação de perfis LAN automática/manual, Internet e offline local após fechar/reabrir o app; nenhum save slot foi sobrescrito.
- [ ] Verificar backup `before` e `after`, restauração confirmada, mesma ROM e slots manuais intocados.
- [ ] Alternar presets Maizo/CTR/ARMS/PPSSPP, editar individualmente A/B/X/Y/L/R/Start/Select, alternar e confirmar que edições persistem no perfil correto.
- [ ] Medir FPS 10 min com filtros Original/Scanlines/Soft/CRT Soft, comparar custo no HUD, sem aumento de velocidade da emulação.
- [ ] Verificar FPS medido e FPS nominal NTSC/PAL, mudança de janela e pausa; confira média da espera pelo peer no modo LAN e VPN.
- [ ] ROM com CRC32+SHA1 No-Intro conhecida aparece como identificada por checksum; ROM modificada volta para correspondência heurística.
- [ ] Habilitar/desabilitar download de capas; imagens aparecem na grade sem sobrecarregar rede/dados móveis, cache offline é reutilizado.
- [ ] Configurações → DEV: PIN incorreto não desbloqueia; `9090` habilita menu. Compartilhamento permanece somente entre IPs privados com autorização de ambos e direitos de redistribuição.

## Gamepad UI and native START regression tests

- [ ] PS4/PS5/Xbox: physical START starts game / opens in-game game menu, never opens emulator settings.
- [ ] Central PS/Xbox GUIDE opens/closes app settings when Android exposes the key; record firmware behavior when Android consumes Home.
- [ ] D-pad and analog stick move focus across all ROM covers, favorite/recent chips, settings groups, multiplayer cards and Credits.
- [ ] Gamepad A activates all focused buttons/switches/dialog actions; gamepad B closes dialogs and returns from sub-pages.
- [ ] L1/R1 cycles app tabs without triggering in-game inputs.
- [ ] One and two controllers preserve SNES Start and Select independently; touch gameplay remains unaltered.
- [ ] Online gameplay opens the overlay without pausing only one peer; avoids netplay state divergence.

## MZNP v6 — transferência remota
- [ ] DEV 9090 ativa o compartilhamento avançado por padrão, OFF persiste.
- [ ] HTTPS diretório + relay TLS com certificado válido em duas redes diferentes.
- [ ] Cliente com ROM diferente ou sem ROM recebe oferta e pode rejeitar antes de bytes.
- [ ] Host que rejeita mantém partida com cliente que já tem a mesma ROM.
- [ ] SHA-256 da ROM verificado e <=64 MiB; transferência falhada não deixa arquivo parcial.
- [ ] Relay sem TLS não transmite ROM; cliente com ROM igual ainda pode jogar.
- [ ] Certificado inválido causa erro (nunca aceitar sem autenticação).
