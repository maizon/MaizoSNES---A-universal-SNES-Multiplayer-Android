# MaizoSNES v1 task status

Implemented: dual ABI configuration and CI checks; offline SNES title/region
reader and tests; launcher 2/3/4 columns and list, favorites and recents,
ROM details with patch and delete; gamepad A/B/D-pad routing outside game;
Start/Mode pause/menu input routing; private-LAN opt-in ownership prompt for
default-enabled ROM transfer; secure signed-release workflow definition.

Existing preserved: Snes9x, native audio/video, SAF/ZIP, manual LAN, TCP
relay, core settings handshake, SHA-256, IPS/BPS, state slots and touch
editor. All changes need CI and hardware smoke tests to confirm regression
safety. The source updates themselves are not proof of on-device success.

Pending: xdelta/VCDIFF, TheGamesDB authorized API, exact supplied icon,
per-control independent sizing/opacity and full-screen drag constraints,
full gamepad focus across all Settings, 30-second clip capture, per-slot
thumbnail/resume, fast-forward/rewind, haptics, final Material 3 Expressive
UI, verified real WebRTC NAT traversal, production key secrets, signed
artifact and GitHub official stable release. See release workflow for gate.
