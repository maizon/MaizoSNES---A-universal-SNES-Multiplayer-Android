#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHA="ac0d00a7fc01166f84c7bac01d1084ebfd2b865e"
DEST="$ROOT/app/src/main/cpp/third_party/fceumm"
if [[ ! -d "$DEST/.git" || "$(git -C "$DEST" rev-parse HEAD 2>/dev/null || true)" != "$SHA" ]]; then
  rm -rf "$DEST"
  mkdir -p "$(dirname "$DEST")"
  git init --quiet "$DEST"
  git -C "$DEST" remote add origin https://github.com/libretro/libretro-fceumm.git
  git -C "$DEST" fetch --quiet --depth=1 origin "$SHA"
  git -C "$DEST" checkout --quiet --detach FETCH_HEAD
fi
test "$(git -C "$DEST" rev-parse HEAD)" = "$SHA"
test -s "$DEST/Makefile.libretro"
cmp -s "$DEST/Copying" "$ROOT/LICENSES/fceumm-gpl2.txt"

if [[ "${1:-}" == "--build" ]]; then
  NDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}/ndk/26.3.11579264"
  BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
  test -x "$BIN/aarch64-linux-android23-clang"
  for ABI in armeabi-v7a arm64-v8a; do
    if [[ "$ABI" == "arm64-v8a" ]]; then
      CC="$BIN/aarch64-linux-android23-clang"
    else
      CC="$BIN/armv7a-linux-androideabi23-clang"
    fi
    make -C "$DEST" -f Makefile.libretro platform=unix clean >/dev/null
    # Compiler/linker flags are ENV vars, not make command-line assignments.
    # The upstream Makefile must still append COREDEFINES, including
    # FCEU_VERSION_NUMERIC=9900, and its required -lm linkage.
    CFLAGS="-O2 -fPIC -D__ANDROID__" \
    LDFLAGS="-Wl,-z,max-page-size=16384" \
      make -C "$DEST" -f Makefile.libretro -j 3 platform=unix \
        HAVE_HDPACK=0 HAVE_NTSC=0 CC="$CC" AR="$BIN/llvm-ar"
    test -s "$DEST/fceumm_libretro.so"
    mkdir -p "$ROOT/app/src/main/jniLibs/$ABI"
    cp "$DEST/fceumm_libretro.so" "$ROOT/app/src/main/jniLibs/$ABI/libfceumm_libretro.so"
  done
fi
mkdir -p "$ROOT/app/src/main/assets/licenses"
cp "$DEST/Copying" "$ROOT/app/src/main/assets/licenses/fceumm-gpl2.txt"
echo "FCEUmm source pinned to $SHA; license GNU GPLv2"
