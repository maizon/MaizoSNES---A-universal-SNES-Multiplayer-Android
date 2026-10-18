# MaizoSNES — device smoke-test checklist

This checklist is manual: a successful GitHub Actions build proves compilation and package checks only. It is not a substitute for testing on an actual Android device.

## Before testing

- Install the debug APK from a successful workflow run on an ARM64 Android device.
- Use a legally obtained, known-good raw SNES `.sfc` or `.smc` file. No test ROM is stored in this repository.
- Start with device media volume at a safe level and close other audio-heavy apps.

## Basic gameplay

- [ ] App opens without a crash or native-library loading error.
- [ ] A valid ROM can be selected through Android's document picker.
- [ ] A visible game image appears in the SurfaceView.
- [ ] Touch D-pad and every virtual action button respond; simultaneous direction + action is recognized.
- [ ] A physical controller's D-pad, ABXY, start/select, shoulder buttons and analog stick work where the device exposes them.
- [ ] Audio is audible, reasonably synchronized and does not loop stale samples after changing ROMs.
- [ ] An invalid file produces an error without crashing.
- [ ] A second ROM replaces the first; the previous game/audio does not continue.

## Save-state safety

- [ ] Save a state and confirm a nonempty file is written.
- [ ] Continue playing, restore the saved state, and check the game returns to the saved point.
- [ ] Attempt to load a state created from different ROM bytes; it must be rejected.
- [ ] Attempt to load a renamed, random or corrupted file; it must be rejected without a crash.
- [ ] Reset requires confirmation and warns that unsaved progress will be lost.

## Lifecycle and stability

- [ ] Press Home or open another app: emulation and audio pause.
- [ ] Return to the app: the game resumes only when its surface is attached.
- [ ] Recreate the Activity or rotate the device: no crash, leaked surface, stuck input or dead audio thread.
- [ ] Repeatedly open and close ROM/state pickers without freezing.
- [ ] Run for at least 10 minutes; check for excessive heat, audio underruns and progressive slowdown.

## Test report

Record device model, Android version, APK commit SHA, ROM identity (do not upload the ROM), failed checklist items and relevant logcat excerpts. Keep ROM files out of bug reports.

Do not mark gameplay as validated until this checklist has been exercised on a physical device.
