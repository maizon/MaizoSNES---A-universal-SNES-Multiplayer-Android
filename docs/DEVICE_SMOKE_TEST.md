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
