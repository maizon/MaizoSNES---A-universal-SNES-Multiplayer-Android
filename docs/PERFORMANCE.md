# Performance baseline and acceptance gates

Status: **baseline not measured on physical Android devices**. A successful GitHub Actions build is not an FPS or latency benchmark.

## Measurement protocol

Use the same redistributable homebrew ROM and identical build on ARMv7 and ARM64. Record device model, Android version, refresh rate, thermal state, battery saver, transport, and ROM SHA-256. Capture at least 10 minutes per scenario, with warm-up excluded.

| Scenario | Baseline FPS | p95 frame time | p99 stall | RTT/jitter | Result |
| --- | --- | --- | --- | --- | --- |
| Offline | Not measured | Not measured | Not measured | N/A | Pending device |
| LAN TCP Lockstep | Not measured | Not measured | Not measured | Not measured | Pending two devices |
| LAN UDP GekkoNet | Not measured | Not measured | Not measured | Not measured | Pending two devices |
| Online WSS TCP | Not measured | Not measured | Not measured | Not measured | Pending two networks |
| Online WebRTC | Not measured | Not measured | Not measured | Not measured | Pending ICE connected |
| Background/resume | Not measured | Not measured | Not measured | Not measured | Pending device |

## Regression gates

- No incorrect claim that TCP Lockstep is UDP/WebRTC rollback.
- Record observed stalls over 500 ms and correlate with state serialization, network waits, rendering and audio.
- Measure freeze/unfreeze time before enabling any limited-saving optimization; never infer improvement from compilation alone.
- If UDP/ICE negotiation fails, verify both peers select TCP without UI deadlock.
- Verify SHA-256 after authorized ROM transfer before beginning emulation.
- Compare ARMv7 and ARM64 state checksums at deterministic checkpoints.
- Keep -ffast-math and -Ofast disabled to protect determinism.

Do not publish numerical performance improvements until before/after traces exist.
