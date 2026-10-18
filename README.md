# MaizoSNES

**Android-first SNES emulator project**, built around the pinned Snes9x 1.63 libretro core.

> **Status: experimental integration.** The Android shell, native core build path, SAF ROM picker, SurfaceView video bridge, touch controls and PCM audio queue are being integrated. A green CI build only confirms compilation/package checks; a real game still requires device validation. Do not describe the app as fully tested until ROM loading, image, sound, input and lifecycle have been exercised on hardware.

## Current technical direction

- Upstream: [snes9xgit/snes9x](https://github.com/snes9xgit/snes9x), tag **1.63**, pinned commit `921f9f7b83660eb44ad263022a57a4a029057c37`.
- The build script fetches the exact upstream revision at build time; third-party source code is not committed to this application repository.
- CMake uses an explicit source list following upstream `libretro/Makefile.common`; the core is compiled with `__LIBRETRO__` and the required upstream include directories.
- ROMs are selected through Android's Storage Access Framework. The app does not bundle or download games.
- The initial native build targets `arm64-v8a`; `armeabi-v7a` has not been enabled yet.
- Touch input and a native window output are part of the first integration slice. Multiplayer, save-state UI, configurable shaders and a formal performance HUD remain future work.

## Build

Requirements: JDK 17, Android SDK Platform 35, NDK `26.3.11579264`, CMake `3.22.1`, and Git.

From the repository root:

```bash
bash scripts/fetch_snes9x.sh
./gradlew --no-daemon assembleDebug
```

The script verifies the source commit and compares the upstream Snes9x and NTSC-filter licenses with the notices retained under `LICENSES/`. The source checkout and generated Android license assets are ignored by Git and are not meant to be committed. GitHub Actions runs the same preparation step before compiling.

## Licensing and distribution restrictions

The Snes9x license grants rights for **non-commercial purposes** and states that commercial rights are not granted. The NTSC filter has its own license. See `LICENSE_DECISION.md`, `THIRD_PARTY_NOTICES.md`, and `LICENSES/`. Do not monetize or publish an APK until the intended distribution has been reviewed against all applicable license conditions. This README is not legal advice.

## Roadmap

1. Validate the first core-linked Android build and fix any compile/link issues.
2. Test loading known-good `.sfc` / `.smc` files, video, audio, touch input, rotation and lifecycle on devices.
3. Add save-state/restore UI, controller mapping, aspect-ratio settings and frame-time diagnostics.
4. Implement LAN multiplayer only after deterministic input and state behavior are tested.
5. Consider internet netplay and rollback after LAN synchronization is reliable.
6. Revisit ARMv7 support, compatibility matrix, package contents and licensing before release.

## ROM policy

No ROMs, BIOS files or copyrighted game assets are included. Users must select game files they are legally entitled to use. ZIP archives are not accepted by the initial raw-ROM path.
