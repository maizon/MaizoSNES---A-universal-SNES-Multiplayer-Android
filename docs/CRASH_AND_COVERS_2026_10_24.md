# Stability and box art — MaizoSNES Android

## Root-cause analysis of reported closes (2026-10-24)

1. **Confirmed code-level teardown race:** the prior Activity called
   `NetplaySession.stop()` and immediately `NativeBridge.nativeShutdown()`,
   while the netplay/Gekko worker could still execute libretro callbacks.
   The native code could destroy FCEUmm while a worker held its entry point.
   Stop is still nonblocking in normal UX, but destruction now waits up to
   1.8 seconds for the worker to stop before native unload. If the worker
   does not stop, native unload is deliberately skipped to avoid a
   use-after-free; process shutdown reclaims remaining resources.
2. **Confirmed AudioTrack stop/start hazard:** the worker previously used a
   shared running flag. A rapid stop followed by start could reactivate an old
   writer using a released AudioTrack. A generation token prevents this,
   and track release waits for the writer to leave.
3. **NES rollback risk (not proven as crash root):** FCEUmm serialization
   is now tested three times only when negotiating NES rollback; inconsistent
   or >2 MiB states choose the existing TCP fallback. The established SNES
   handshake, transport, settings, rollback or core behavior is not modified.
4. **Local interruption breadcrumbs:** the app records only a small local
   text phase (SNES/NES, offline/online). The last interrupted session is
   shown on next launch and in the existing diagnostic export; it does not
   capture native SIGSEGV backtraces, send data or imply definite crash
   (Android can kill background processes).

## Explicit-consent box art

- The old `automaticCovers` setting now means *ask before downloading
  after import*. It **never** automatically fetches covers.
- Single ROM and bulk SAF/device scans show one confirmation for newly
  imported titles. Choosing **Agora não** makes zero box-art requests.
- Only the user-initiated **Baixar capas ausentes** command uses network.
- Already cached/downloaded and custom covers are displayed offline even
  if download prompts are disabled, and are never fetched again merely
  because the library is opened.
- At most one foreground-library download sequence runs; opening any game
  cancels the sequence and in-game cover loading performs no HTTP requests.
- Bitmap previews are bounded in RAM for low-memory ARMv7 devices.

## Physical validation still required

CI checks compile/tests and both ABIs, not native runtime stability.

Reproduce and inspect crash with a permitted/homebrew NES ROM:
- NES solo for 15 minutes then open/close game 20 times;
- LAN UDP NES P1/P2 for 15 minutes, then Wi-Fi+4G and 4G+4G;
- verify fallback if state preflight is rejected;
- switch to another app and exit game while LAN/online netplay is connected;
- test rapid audio device changes and game re-entry;
- import single/ZIP/bulk and decline covers; confirm no HTTP requests;
- confirm existing cache is visible offline and no duplicate downloads;
- repeat prior SNES tests unchanged.

If an Android native crash persists, collect its tombstone/logcat
(`adb logcat -b crash -d`) immediately after the incident, including the
signal, faulting library and stack, with personal/ROM information removed.
Do not claim root cause for NES-in-game crashes without device evidence.
