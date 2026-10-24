# MaizoSNES 2.0 — Landscape Console Experience

This single conservative patch updates only Jetpack Compose presentation and
local library selection; no new online API calls or uploaded avatars.

- **Console home:** featured game with cached cover, ROM header region,
  direct Play/Continue and Details.
- **Multiplayer lobby:** local initials avatars for recent, LAN, online and
  relay room cards; responsive landscape header and horizontally scrollable
  connection-mode options reusing exactly the previous callbacks.
- **Library:** fast Todos/Favoritos/Recentes/Coleções shelves while retaining
  GRID/LISTA/FILEIRA/COLEÇÃO, search, automatic covers and custom covers.
- **In-game menu:** immediate Resume, portable 4:3, pixel-perfect and touch
  visibility controls, alongside the original settings and status overlay.
- **Portable landscape:** responsive console navigation and decorative side rails
  for non-stretched aspect modes. No change to video surface placement/input.

## Regression checklist

CI: JVM unit tests + dual ABI debug APK + verify no ROM or BIOS.
Device: empty library and 100 titles; cover fallback, search, favorites,
gamepad navigation, portrait/landscape on compact phone; Play/Continue/Details;
lobby LAN, mobile-to-mobile and Wi-Fi/4G; change only video/touch in active game.
Real multiplayer stability still requires devices, not just green CI.

Protected: Snes9x, GekkoNet, TCP fallback, WebRTC, WSS/Railway, ROM sharing,
handshake, rollback state, jitter/buffers, foreground service and all ABIs.
