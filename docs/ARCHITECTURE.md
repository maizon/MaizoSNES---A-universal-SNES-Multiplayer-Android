# Arquitetura técnica — MaizoSNES

## Frontend / UI

- Jetpack Compose Material 3: biblioteca, tela landscape imersiva, ajustes e aba multiplayer.
- Importação local via SAF uma única vez; ROMs `.sfc`, `.smc` ou ZIP com ROM única são guardadas em filesDir/roms. Importador impõe limite de 64 MiB de bytes descompactados.
- Vídeo SurfaceView + ANativeWindow RGB565, com proporções definidas pelo frontend. Não há shader OpenGL/Vulkan ainda.
- Controle virtual original com superfície direcional contínua (inclusive diagonais), multitouch com botões de ação, ajuste de escala/opacidade e grupos arrastáveis no modo EDITAR, salvo em SharedPreferences global/por jogo.
- Teclado e dispositivos Android GAMEPAD/JOYSTICK têm máscaras independentes e são combinados por porta. Não há auto-config avançada por fabricante.
- AudioTrack PCM16 estéreo com tentativa de modo de baixa latência (API 26+) e buffer respeitando mínimo do Android; latência é dependente do dispositivo.

## Núcleo

- Snes9x 1.63 (libretro) fixado e baixado em scripts/fetch_snes9x.sh; direitos/licenças originais preservados.
- Uma thread C++ executa `retro_run` no modo offline; configurações do core e snapshots protegem-se por mutex.
- Contrato Kotlin/JNI inclui ports 0 e 1, gamepad bitmasks, states com fingerprint de ROM, input, vídeo e áudio.
- No modo netplay, a thread offline é parada e o peer TCP assume o avanço quadro-a-quadro; nunca permitir dois clocks retro_run concorrentes.
- Lockstep v1 e estados em docs/NETPLAY.md.

## Limites

- ARM64 apenas, não há build ARMv7 configurada.
- Save RAM persistente (SRAM) independente dos save states não foi homologada.
- No netplay, opções divergentes ou ROM diferente bloqueiam conexão; identidade do core é um identificador do build, **não hash binário**. Comparar hashes de memória serializada pode acusar dessíncronia em núcleos não determinísticos.
- ROM e jogos não são incluídos no repositório ou APK. Obras/licenças de terceiros em LICENSES/ e THIRD_PARTY_NOTICES.md.

## Recursos adicionais desta fase

- CheatCatalog consulta índice fixado na árvore pública de libretro-database e baixa apenas arquivos `.cht` selecionados automaticamente pelo nome da ROM ou título ASCII do cabeçalho SNES. Dados em cache privado; fonte com atribuição CC BY-SA 4.0. JNI limpa e recompõe grupos de cheats via `retro_cheat_reset` e `retro_cheat_set`, impondo **limite de 190 bytes ASCII por código** devido ao `strcpy` vulnerável do core Snes9x 1.63. Cheats são desativados durante lockstep.
- `LanDiscovery.kt` usa broadcast UDP 55436 (anúncio somente enquanto host espera), busca automática por interface IPv4 com fallback manual. O app utiliza Wi-Fi multicast lock durante anúncio/scan quando disponível.
- MZNP v2 negocia hashes de ROM e opções; uma ROM divergente pode ser oferecida **apenas** para pares IPv4 privados e somente com consentimento explícito bilateral e direitos legais de distribuição. O cliente verifica SHA-256, importa na pasta privada e chama nativeLoadRom antes de confirmar o estado compartilhado.
- A barra in-game é recolhida a círculo semitransparente de 44 dp e reabre sob toque.
