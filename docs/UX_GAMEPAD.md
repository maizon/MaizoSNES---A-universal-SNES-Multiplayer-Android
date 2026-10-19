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
