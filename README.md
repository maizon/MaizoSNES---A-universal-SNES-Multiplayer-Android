# MaizoSNES

**Android-first SNES emulator project**, built around the pinned Snes9x 1.63 libretro core.

> **Status: experimental integration.** GitHub Actions has successfully compiled and packaged the native core, ROM-picker bridge, native-window video output, touch input and PCM audio queue into an ARM64 debug APK. These checks validate compilation and package contents, not correct gameplay. ROM loading, image, sound, input, timing and lifecycle still require real-device testing.

## Current technical direction

- Upstream: [snes9xgit/snes9x](https://github.com/snes9xgit/snes9x), tag **1.63**, pinned commit `921f9f7b83660eb44ad263022a57a4a029057c37`.
- `scripts/fetch_snes9x.sh` fetches that exact revision and verifies the upstream license files. Core source is fetched during local/CI builds and is not committed to this application repository.
- CMake uses an explicit list based on upstream `libretro/Makefile.common`, with the libretro frontend as the portable core host.
- ROMs are selected through Android's Storage Access Framework and read into memory. The app does not bundle or download games. The first ROM path accepts raw `.sfc` / `.smc` files, not ZIP archives.
- The current build targets `arm64-v8a` only. ARMv7 is not enabled.
- The app includes a native-window video surface, touch controls, a libretro audio callback queue and Android `AudioTrack` output. Rendering currently copies into a native window; a dedicated GPU renderer/shader pipeline is not implemented yet.
- The frame loop derives its interval from the core's reported timing where available. Hardware frame pacing and audio sync still need device tests.

## Build

Requirements: JDK 17, Android SDK Platform 35, NDK `26.3.11579264`, CMake `3.22.1`, and Git.

From the repository root:

```bash
bash scripts/fetch_snes9x.sh
./gradlew --no-daemon assembleDebug
```

GitHub Actions prepares the pinned source, verifies that the ARM64 native library is packaged, checks that both third-party license texts are present in the APK, and publishes the debug APK as the `app-debug` workflow artifact.

## Licensing and distribution restrictions

The Snes9x license grants rights for **non-commercial purposes** and states that commercial rights are not granted. The NTSC filter has a separate LGPL license. See `LICENSE_DECISION.md`, `THIRD_PARTY_NOTICES.md`, and `LICENSES/`. Do not monetize or publish an APK until the intended distribution has been reviewed against all applicable license conditions. This README is not legal advice.

## Roadmap

1. Test the APK on a physical Android device with a known-good ROM: boot, image, sound, input, rotation and lifecycle.
2. Fix runtime issues and add save-state/restore, controller mapping and aspect-ratio options.
3. Add a dedicated GPU renderer, shaders and frame-time diagnostics after baseline behavior is stable.
4. Implement local Wi-Fi multiplayer only after game-state determinism and input synchronization have been tested.
5. Consider internet netplay/rollback after LAN synchronization works.
6. Revisit ARMv7 support, compatibility matrix, package contents and licensing before release.

## ROM policy

No ROMs, BIOS files or copyrighted game assets are included. Users must select game files they are legally entitled to use.
