# Library

New launcher supports 2/3/4 columns, list, Favorites, Recents, and
ROM details containing title, region, cover, game/patch/favorite/rename/delete.

Offline title and region come from SNES header candidates (LoROM, HiROM,
ExHiROM), including optional copier headers. Header warnings are heuristic,
not proof of corruption. Existing No-Intro metadata lookup uses exact
size+CRC32+SHA-1 when available. Existing cached Libretro boxart is kept.
TheGamesDB API integration is pending API key and terms review; no claim
that covers are fetched from TheGamesDB.

All ROMs are user-provided via SAF/ZIP/scan. No ROMs ship inside APK.
Favorites and recents persist locally. Active ROM deletion is blocked.
