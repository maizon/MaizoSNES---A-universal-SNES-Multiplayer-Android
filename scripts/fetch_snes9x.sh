#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SNES9X_SHA="921f9f7b83660eb44ad263022a57a4a029057c37"
SNES9X_TAG="1.63"
DEST="$ROOT/app/src/main/cpp/third_party/snes9x"

if [[ -d "$DEST/.git" ]] && [[ "$(git -C "$DEST" rev-parse HEAD 2>/dev/null || true)" == "$SNES9X_SHA" ]]; then
  echo "Snes9x already pinned to $SNES9X_SHA"
else
  rm -rf "$DEST"
  mkdir -p "$(dirname "$DEST")"
  git init --quiet "$DEST"
  git -C "$DEST" remote add origin https://github.com/snes9xgit/snes9x.git
  git -C "$DEST" fetch --quiet --depth=1 origin "refs/tags/$SNES9X_TAG"
  git -C "$DEST" checkout --quiet --detach FETCH_HEAD
fi

ACTUAL_SHA="$(git -C "$DEST" rev-parse HEAD)"
if [[ "$ACTUAL_SHA" != "$SNES9X_SHA" ]]; then
  echo "Unexpected Snes9x revision: $ACTUAL_SHA (expected $SNES9X_SHA)" >&2
  exit 1
fi

if [[ ! -s "$DEST/libretro/Makefile.common" || ! -s "$DEST/libretro/libretro.cpp" ]]; then
  echo "Pinned Snes9x tree is incomplete" >&2
  exit 1
fi

# Fail closed if the repository's retained notice drifts from upstream.
if ! cmp -s "$DEST/LICENSE" "$ROOT/LICENSES/snes9x.txt"; then
  echo "LICENSES/snes9x.txt does not match the pinned upstream LICENSE" >&2
  exit 1
fi
if ! cmp -s "$DEST/filter/snes_ntsc-license.txt" "$ROOT/LICENSES/snes_ntsc-license.txt"; then
  echo "LICENSES/snes_ntsc-license.txt does not match the upstream filter license" >&2
  exit 1
fi

mkdir -p "$ROOT/app/src/main/assets/licenses"
cp "$DEST/LICENSE" "$ROOT/app/src/main/assets/licenses/snes9x.txt"
cp "$DEST/filter/snes_ntsc-license.txt" "$ROOT/app/src/main/assets/licenses/snes_ntsc-license.txt"

echo "Prepared Snes9x $SNES9X_TAG at $ACTUAL_SHA"
echo "Build inputs are fetched at CI time; upstream core source is not committed to this repository."
