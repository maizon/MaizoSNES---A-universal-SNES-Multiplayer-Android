# Auditoria de UX MaizoSNES

Atualizado: 2026-10-18. Auditoria baseada nos arquivos da branch work/android-foundation. CI só verifica compilação/empacotamento; runtime deve ser testado manualmente em hardware real.

## Estado anterior à remodelação

A tela inicial continha o SurfaceView dentro de uma coluna rolável, controles abaixo do vídeo e abas com ações de exemplo. A seleção de ROM abria o picker do Android a cada partida. Não havia orientação dedicada, modo imersivo, editor, biblioteca persistente, seleção real de proporção ou opções runtime do núcleo. Multiplayer era apenas uma mensagem.

## R1–R9 depois desta fase

| Requisito | Estado no código | Verificação humana pendente / limite |
| --- | --- | --- |
| R1 Biblioteca in-app | Implementado no código: importa .sfc/.smc e ZIP com ROM única para filesDir/roms, lista, renomeia e abre. Picker usado apenas na importação inicial. | Testar duplicatas, arquivos grandes e renomeação em device. ZIP limitado a 64 MiB descompactados; testar no aparelho. |
| R2 Gameplay dedicado | Implementado no código: rota separada, landscape, superfície principal, pausa/menu/back para biblioteca. | O Manifest declara orientation|screenSize|keyboardHidden para não recriar a Activity ao entrar em landscape; validar orientação e retorno das barras em versões Android distintas. |
| R3 Aspect ratio | Implementado no código: escala inteira de referência 256×224, 4:3, 16:9, esticar e preencher/crop; aplica em runtime. | Modos Hi-Res e geometria variante requerem teste; não há renderer GPU especializado. |
| R4 Menu do núcleo | Implementado no código: opções expostas pelo Snes9x 1.63 agrupadas em vídeo/áudio/núcleo; GET_VARIABLE e GET_VARIABLE_UPDATE conectados ao bridge JNI. | Confirmar cada efeito em gameplay. Algumas opções podem alterar geometria ou temporização. |
| R5 Controles flutuantes | Implementado no código: D-pad deslizante e diagonal, ABXY, L/R, Select/Start sobre o vídeo; alvos de toque independentes e semi-transparentes. | Testar multitouch e conforto em hardware. |
| R6 Editor de layout | Implementado no código: arrasto direto do D-pad e do grupo ABXY dentro do jogo, além de sliders para deslocamento X/Y, escala e opacidade; perfil global e por ROM. | Testar drag e persistência dos perfis na tela real. |
| R7 Imersão | Implementado no código: orientação landscape e barras ocultas durante gameplay; restauração ao sair. | Android pode revelar temporariamente as barras por gesto do sistema. |
| R8 LAN | Implementado como experimento TCP: segunda porta libretro, handshake SHA-256 de ROM/opções, save-state inicial do host, lockstep por frame, hashes a cada 120 quadros e stop/timeout. Também há dois gamepads no mesmo aparelho. | NÃO validado em dois dispositivos; sem rollback, criptografia, matchmaking ou NAT traversal. Ver docs/NETPLAY.md. |
| R9 Retrabalho UI | Implementado no código: biblioteca, gameplay, LAN e ajustes são rotas distintas com navegação clara. | Validação visual e acessibilidade continuam sendo teste humano. |

## Guardrails de regressão

- Manter a superfície nativa ativa em gameplay.
- Ao voltar à biblioteca, pausar sem destruir o core para permitir continuar.
- Parar o consumidor de áudio antes de trocar ROM/restaurar estado e reativá-lo após.
- Manter a checagem de fingerprint nos save states.
- Não empacotar ROMs, BIOS, capas ou assets de jogos.

Siga docs/DEVICE_SMOKE_TEST.md. Não tratar CI verde como validação de device.
