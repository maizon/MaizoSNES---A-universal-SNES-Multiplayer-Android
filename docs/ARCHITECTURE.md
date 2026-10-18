# Architecture and implementation plan

## Modules (planned)
- **Android UI (Compose):** library, settings, session setup, touch controls and diagnostics.
- **Emulation adapter (JNI):** stable Kotlin-facing API for initialize/load/run-frame/input/audio/video/save-state.
- **Core:** Snes9x candidate; integration is gated on a license review and a reproducible native build for arm64-v8a and armeabi-v7a.
- **Video:** native frame buffer to OpenGL ES texture; shaders are a separate rendering stage so filters do not alter emulation timing.
- **Audio:** low-latency audio callback/ring buffer; avoid blocking the emulation thread.
- **Netplay:** deterministic input packets first; rollback only after save/load-state and determinism tests pass.
- **Diagnostics:** frame time, emulation speed, audio underruns, network RTT/jitter/loss, rollback count and desync detection.

## Netplay protocol requirements
Packets should carry protocol version, session ID, sequence number, frame number, player ID, controller bitmask, acknowledgement/window data, and integrity checks. Use bounded packet sizes, timeouts, replay protection, and explicit session negotiation. LAN discovery is separate from internet matchmaking. Never trust a peer-provided ROM path or arbitrary file.

## Performance rules
1. Do not do disk/network I/O on the render or emulation thread.
2. Reuse frame/audio buffers; avoid per-frame allocations.
3. Measure frame pacing and input-to-photon latency rather than relying only on average FPS.
4. Apply shaders on GPU, but retain a simple low-cost fallback.
5. Treat 50/60 Hz game timing separately from display refresh rate.
6. Rollback requires deterministic replay and tested state serialization; do not advertise it before validation.

## Current implementation status
The first commit is a UI/build foundation only. The library buttons are informative placeholders; there is no ROM loader, native emulator core, actual UDP session, rollback, or shader pipeline yet.
