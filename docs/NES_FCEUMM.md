# MaizoSNES + FCEUmm NES/Famicom integration

## Native architecture
FCEUmm is pinned at commit `ac0d00a7fc01166f84c7bac01d1084ebfd2b865e`
(https://github.com/libretro/libretro-fceumm). It is built in CI with the
Android NDK as `libfceumm_libretro.so` separately for ARMv7 and ARM64.

The existing Snes9x 1.63 sources, CMake flags and libretro ABI remain
unchanged. The Android JNI frontend switches libretro *function pointers*
only at game-load boundaries under the existing core mutex. The same
frame/audio/video pipeline, state serialization, touch/gamepad callbacks,
GekkoNet UDP rollback, WebRTC and TCP fallback are reused. The network
protocol and relay server are untouched. Users must still play with the
same ROM and APK version at both ends.

FCEUmm requires `need_fullpath`, so imported NES files are loaded by the
core from app-private `filesDir/roms/`. NES files supported:
`.nes`, `.unf`, `.unif`, `.fds`. FDS additionally needs a legally acquired
`disksys.rom` firmware placed in the application's system directory; no
firmware or commercial ROM is bundled.

## Shared user experience
- Existing library, ZIP/SAF import and device scan, favorite/recent collection
  filtering, profiles, screenshots and game menu.
- Separate NES online cover-art catalog/cache so SNES covers are not confused
  with NES. Local custom covers use distinct keys.
- Existing touch/gamepad layouts; NES shows B and A only, plus its D-pad,
  Start and Select. SNES L/R/X/Y and multitap retain their existing behavior
  only on Snes9x games. NES standard ports P1/P2 are supported.
- Same video scaling/filters, audio controls, pause, saves and state management.
- LAN/online rooms and ROM sharing with bilateral authorization inherit their
  previous behavior; no extra server load.

## Testing and honest limits
`./gradlew testDebugUnitTest assembleDebug` plus Node network tests and
dual-ABI packaging checks run on CI. Real device tests are required to confirm
FCEUmm runs games, its save-state determinism across ARMv7/ARM64 and
GekkoNet rollback stability over Wi-Fi/LAN, Wi-Fi/4G and 4G/4G.
FDS BIOS, cheats and NES-specific core options need validation; don't imply
SNES-only settings perform a corresponding NES feature. The existing
SNES-specific cheat catalog is not misapplied to NES.

## Licensing alert
FCEUmm is GNU GPLv2, with full license in LICENSES/fceumm-gpl2.txt.
Snes9x uses noncommercial terms. Loading both cores into one Android
process may raise GPL compatibility issues when distributing the complete
APK. Using separate shared libraries does not automatically remove those
obligations. Legal review and permission may be needed before public
distribution or monetization. Both sources are fetched from pinned upstream
revisions; game ROMs and BIOS are never included.
