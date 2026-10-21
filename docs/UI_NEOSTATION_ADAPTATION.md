# MaizoSNES UI — NeoStation-inspired adaptation

Reference: NeoStation by Miguel Soto / contributors, https://github.com/misobadev/neostation-frontend (GPL-3.0-or-later). The user supplied a ZIP snapshot for study.

## Licensing boundary

NeoStation is a Flutter application released under GPLv3; MaizoSNES uses Android Jetpack Compose and a Snes9x core with separate noncommercial terms. **Do not copy NeoStation's Dart source, assets, fonts, screenshots or branding into MaizoSNES** without completing a compatibility and redistribution review. The present implementation draws on general UI design concepts only; it is independently written Kotlin Compose. Attribution to NeoStation is for design inspiration, not endorsement or a claim of code reuse.

## Implemented first step

- New dark navy/blue/teal presentation palette.
- Wide layouts (720dp+) use a persistent, controller-focusable left sidebar with Library, Multiplayer, Settings, Credits and author credit.
- Narrow layouts retain bottom navigation and all existing screens and callbacks.
- Gameplay rendering, ROM import, LAN/online sessions, authorized transfer, and native core are untouched.

## Full migration still pending

- Responsive hero/library with cover grid, recent games, favorites, details, search and sort parity.
- Controller-focusable game detail panels, responsive navigation transitions, richer settings and cover placeholders.
- Tablet/TV layouts and accessibility verification (TalkBack, large fonts, contrast).
- Dedicated UI instrumentation/screenshot tests and two-device manual regression test.

## Release gate

Do not label this a completed NeoStation UI port until all screens have been adapted and CI/device verification is complete. No Flutter runtime was added.
