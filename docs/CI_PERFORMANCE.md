# CI performance and cache validation

Last updated: 2026-10-18

## Baseline before fixes

The first workflow runs did not produce a valid timing baseline because the build failed before an APK could be uploaded.

| Commit | Workflow run | Result | Build step | Notes |
| --- | --- | --- | --- | --- |
| `0fd6e67fd6681ce912e45b2348189adb45c514e2` | [39135465649](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39135465649) | Failed | 2m 25s | Kotlin syntax error in `MainActivity.kt` at lines 175–176; no APK artifact. |
| `0fd6e67fd6681ce912e45b2348189adb45c514e2` | [39135460979](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions/runs/39135460979) | Failed | Pending log review | Push-triggered run for the same commit. |

## Current cache strategy

- Gradle User Home caching is configured through `gradle/actions/setup-gradle@v6` using the basic provider.
- The pinned Android NDK and CMake installation directories use an exact-key cache based on the native build configuration.
- The manual `app/.cxx` cache was removed. Until the native core is integrated and compile time justifies the complexity, avoiding stale CMake intermediates is preferable.
- Gradle build caching remains enabled with `org.gradle.caching=true`.
- Gradle configuration cache is disabled until the CI build is green and the JNI scaffold is validated on a physical device.

## Measurements to record

Do not claim a cache speedup until there is a successful APK-producing run. For each successful run, record:

| Run | Commit SHA | Change type | Job duration | `Build debug APK` duration | Gradle cache hit/miss | NDK/CMake cache hit/miss | APK contains `lib/arm64-v8a/libnative-lib.so` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | TBD | First successful build | TBD | TBD | TBD | TBD | TBD |
| 2 | TBD | Kotlin/comment-only change | TBD | TBD | TBD | TBD | TBD |
| 3 | TBD | Native C++ change | TBD | TBD | TBD | TBD | TBD |

Compare runs only when they are on comparable runners and the same workflow/cache configuration. A failed run is not a timing baseline. Cache availability may differ between pull-request and default-branch runs because the basic Gradle cache can be read-only outside the default branch.

## Required native packaging check

The workflow now inspects the generated APK and fails unless `lib/arm64-v8a/libnative-lib.so` is present. It also rejects `libc++_shared.so`, because the scaffold is configured to use the static C++ runtime. A green CI run does not substitute for the separate on-device check: install the APK, launch the app, confirm `JNI retornou 0`, rotate the device, and background/restore the app.
