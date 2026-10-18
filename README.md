# MaizoSNES

**Android-first SNES emulator and multiplayer project.** The goal is a polished, lightweight interface, fast native emulation, GPU-assisted shaders/upscaling, and low-latency multiplayer over Wi-Fi and the internet.

> **Current status: foundation / prototype.** The Android UI and project structure are being established. The SNES core, ROM loading, rendering pipeline, and real netplay are not integrated yet; this repository must not be described as a working emulator until those parts are implemented and tested.

## Project goals
- Native Android app with a modern, responsive interface.
- Snes9x evaluation/integration, with ABI builds for **arm64-v8a** and **armeabi-v7a**.
- OpenGL ES rendering, configurable aspect ratio and shader/filter pipeline.
- Touch controls with customizable layout, plus Bluetooth/USB controller support.
- Wi-Fi LAN sessions first, then internet netplay with deterministic input synchronization and rollback.
- Performance HUD for FPS, frame time, input/network latency, and dropped frames.
- No copyrighted game ROMs or BIOS files bundled.

## Build
Open this repository in a recent Android Studio version with Android SDK Platform 35 and JDK 17. The project uses Gradle Kotlin DSL and Jetpack Compose. A Gradle wrapper will be added once its binary wrapper files can be generated and verified.

## Core and licensing gate
Snes9x is available at https://github.com/snes9xgit/snes9x. Its published license contains non-commercial-use restrictions and specific conditions. Before distributing a build containing Snes9x or derivatives, review the complete license and obtain any permission needed for the intended distribution. The project will not silently bundle a core or claim redistribution rights that have not been established.

## Milestones
1. **Android foundation:** application shell, navigation, settings surfaces, build configuration.
2. **Emulation vertical slice:** legally reviewed core integration, ROM picker, native bridge, audio/video output, save states.
3. **Controls and graphics:** configurable touch layout, controller mapping, OpenGL ES shaders and scaling.
4. **LAN netplay:** packet protocol, session discovery/connection, input synchronization, desync diagnostics.
5. **Internet netplay:** rollback, NAT traversal/relay strategy, latency and packet-loss tests.
6. **Release quality:** CI builds, ABI checks, compatibility matrix, license notices, crash/performance testing.

## Reuse policy
Other projects may be used as references or sources only after reviewing their license and preserving required notices. Do not copy assets, code, or layouts with incompatible licenses. Game ROMs remain the user's responsibility; the app will not download ROMs from third-party sites.

## License
The application code's license will be finalized after the core integration and dependency/license audit. This does **not** relicense third-party components.
