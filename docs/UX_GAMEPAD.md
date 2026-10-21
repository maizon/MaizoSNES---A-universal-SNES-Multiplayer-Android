# MaizoSNES gamepad controls

## In-game
- Physical START (Android KEYCODE_BUTTON_START, 108) is the real SNES
  Start input. It is no longer reserved for opening the settings menu.
- PS/Xbox GUIDE/MODE (KEYCODE_BUTTON_MODE, 110) and the optional
  controller-specific 188 toggle the emulator menu, provided Android
  actually forwards them to the app. Many TVs and phones reserve the
  central Home/Guide key at the OS level: an app cannot override that
  restriction. The translucent on-screen Settings button remains
  available as an alternate route.
- Native B/A/X/Y, SELECT, L/R, D-pad and both player input routes
  are unchanged. Local menu pauses the game; online lockstep cannot
  safely pause only one peer.

## Launcher, menus and dialogs
- D-pad and left analog stick navigate Compose focus targets; analog
  stick uses a 225ms repeat throttle. In the UI, A sends Enter and B
  sends Android Back. Button up/down edges are both passed to Compose.
- L1/R1 cycles the four primary tabs: Biblioteca, Online, Ajustes,
  Créditos. Touch controls continue working alongside gamepad.
- Initial focus targets: import game in Library, first network mode in
  Online, first settings FilterChip in Ajustes, Instagram in Credits,
  Play in game details, Close in game overlay.
- Clickable cards have a visible focus outline, and dialog windows
  directly handle A/B rather than depending on Activity key events.

## Device testing required
Confirm on PS4, PS5, Xbox and generic USB/Bluetooth controllers:
native Start actions really start games, Guide toggles menu when
permitted by system, D-pad + analog traversal of every screen,
FilterChips, dialogs, search fields, scroll-to-focused items,
gamepad reconnect, portrait/landscape and local two-player mapping.

Controller Home/Guide consumption varies by Android firmware. If the
system handles Guide as Home, it may be impossible to capture from
the app; do not silently remap Start as a workaround.


## 21/10/2026 — navegação NeoStation-inspired

O frontend permanece em Jetpack Compose: menu lateral em telas >=720dp, navegação inferior em telas menores e feedback de foco visível nos itens clicáveis. O painel do jogo conserva os mapeamentos de entrada; o botão START continua a atuar como START do SNES; GUIDE/MODE abre o menu quando suportado pelo sistema Android. As provas de acessibilidade e gamepad físico ainda exigem teste em aparelho. Consulte `docs/UI_SMOKE_TEST.md`.


## Customização touch adaptativa e persistência por botão

A sobreposição agora utiliza coordenadas relativas para todos os 10 presets. O D-pad e ABXY ficam nas bordas inferiores, e L/R nos cantos superiores; Select/Start ficam próximos da borda inferior sem substituir o input do jogo. No jogo: engrenagem > EDITAR; arrastar os grupos ou alternar BOTÕES para mover cada controle. A configuração é persistida por título via `TouchLayoutCodec` e segue compatível com JSON de versões anteriores.

Para **tamanho e transparência individuais**, abra Ajustes > Controles > Ajustar botões separadamente, escolha D-Pad/Y/X/B/A/L/R/SELECT/START e use as duas barras. O perfil salva `buttonStyles` contendo `scale` (0.60..1.55) e `opacity` (0.25..1.00), e `buttonShifts` para posicionamento. Restaurar este botão não afeta os demais. Teste físico do alcance multitouch e controles antigos ainda necessário.
