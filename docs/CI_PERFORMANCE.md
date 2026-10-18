# CI performance and cache validation

Last updated: 2026-10-18. Times are from GitHub Actions job/step timestamps (UTC); the PR workflow uses read-only Gradle caching. These measurements are build evidence only, not device runtime measurements.

## Successful builds

| Run / commit | Job duration | Build debug APK | Gradle User Home cache | Android NDK / CMake | Output and validation |
| --- | ---: | ---: | --- | --- | --- |
| [39142534889](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39142534889) `115d0339` | 5m03s | 3m37s (16:55:18–16:58:55Z) | 0 entries restored or saved; read-only basic cache on PR | Attempted restore of ~2.76 GB cache archive; tar restore emitted permission errors, then the pinned SDK install step succeeded. Cache save conflicted with another run. | APK generated, native library and both licence texts verified; `app-debug` artifact uploaded. |
| [39143096230](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39143096230) `65895867` | 5m02s | 3m11s (16:04:11–16:07:22Z) | 0 entries restored or saved; read-only basic cache on PR | Same oversized SDK cache behavior; fallback install step succeeded, save collided with another workflow. | APK generated, native library and both licence texts verified; `app-debug` artifact uploaded. |
| [39146884635](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39146884635) `afac231d` | 5m15s | 3m44s (16:53:19–16:57:03Z) | 0 entries restored or saved; read-only basic cache on PR | Cache key lookup selected a roughly 2.76 GB archive, but tar restore failed with permission errors. The pinned SDK installer step then completed successfully; cache save again reported another job reserving the key. | `BUILD SUCCESSFUL`; APK generated. `lib/arm64-v8a/libnative-lib.so` (3,369,808 bytes uncompressed) and `assets/licenses/snes9x.txt` / `assets/licenses/snes_ntsc-license.txt` verified. Upload artifact `app-debug`: 17,266,144 bytes for the GitHub artifact ZIP. |

### Failed workflow-definition attempt

| Run | Commit | Result | Explanation |
| --- | --- | --- | --- |
| [39147670614](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39147670614) | `b3710ca6` | Failed before a job started | The automated workflow edit split the native-runtime grep block and duplicated the upload section, causing the YAML workflow to be invalid. No runner job or APK was created. The next commit rewrites the workflow file as a complete known-good block and will be checked in CI. |

### Follow-up workflow correction


| [39147743992](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39147743992) | `20519aaf` | Failed at `Locate Android SDK tools` | Workflow generation incorrectly inserted a literal backslash before the `${ANDROID_HOME:-...}` shell expression, preventing SDK path expansion. Replaced by a fully rewritten workflow with the shell variable intact. |
| [39147739984](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39147739984) | `20519aaf` | Cancelled | Push run superseded by the matching pull-request workflow for the same head. |

### Attempts superseded during branch updates

| Run | Commit | Result | Explanation |
| --- | --- | --- | --- |
| [39146645147](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39146645147) | `932d720d` | Cancelled | Push run superseded by the matching pull-request run during the same branch update. |
| [39146650187](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39146650187) | `932d720d` | Cancelled | Cancelled when the landscape recreation fix moved the head to the next commit; it is not a valid build result. |
| [39146881495](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39146881495) | `afac231d` | Cancelled | Push run superseded by the matching pull-request run. |

## CI fixes made in this revision

- Removed caching of the Android SDK `ndk/` and `cmake/` directories. Logs show an approximately 2.76 GB archive and `tar` permission failures during restore, followed by conflicting cache reservation. The job now installs the pinned SDK components directly via the SDK manager, avoiding partial permission-preserving restores.
- Added exact APK byte-size and SHA-256 output to the APK verification step.
- A documentation-only update to this performance log no longer triggers a redundant Android build; other source, workflow, licensing, and UI changes still trigger CI.
- Kept Gradle configuration cache disabled and did not add `-ffast-math`, `-Ofast`, or global LTO.

## What the current CI proves

The successful run at `afac231d` proved Kotlin compilation, native C++ compilation, APK assembly, ARM64 native-library packaging, the two required licence assets, and artifact upload. The archive's uncompressed APK contents totalled 60,703,209 bytes; this is not the on-disk APK size. The next run will print `APK_SIZE_BYTES` and `APK_SHA256` explicitly after assembling the APK.

CI does **not** prove that the UI behaves correctly on an Android device, that touch multitouch works on a particular device, that audio/video remain synchronized during extended play, or that LAN determinism works. Follow `docs/DEVICE_SMOKE_TEST.md`; never label an item device-tested based on a green workflow.
