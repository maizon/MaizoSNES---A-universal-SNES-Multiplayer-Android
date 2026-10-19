#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHA="0d491a4524e10173fc5d2aae22d042d3ae9f2d44"
DEST="$ROOT/app/src/main/cpp/third_party/gekkonet"
if [[ ! -d "$DEST/.git" || "$(git -C "$DEST" rev-parse HEAD 2>/dev/null || true)" != "$SHA" ]]; then
  rm -rf "$DEST"
  mkdir -p "$(dirname "$DEST")"
  git init --quiet "$DEST"
  git -C "$DEST" remote add origin https://github.com/HeatXD/GekkoNet.git
  git -C "$DEST" fetch --quiet --depth=1 origin "$SHA"
  git -C "$DEST" checkout --quiet --detach FETCH_HEAD
fi
test "$(git -C "$DEST" rev-parse HEAD)" = "$SHA"
test -s "$DEST/GekkoLib/include/gekkonet.h"
cmp -s "$DEST/LICENSE" "$ROOT/LICENSES/gekkonet-bsd-2.txt"
mkdir -p "$ROOT/app/src/main/assets/licenses"
cp "$DEST/LICENSE" "$ROOT/app/src/main/assets/licenses/gekkonet-bsd-2.txt"
cmp -s "$DEST/GekkoLib/thirdparty/zpp/LICENSE" "$ROOT/LICENSES/zpp-mit.txt"
cp "$ROOT/LICENSES/asio-boost-1.0.txt" "$ROOT/app/src/main/assets/licenses/asio-boost-1.0.txt"
cp "$ROOT/LICENSES/zpp-mit.txt" "$ROOT/app/src/main/assets/licenses/zpp-mit.txt"

# Prebuilt WebRTC Android artifact's redistributable copyright notices.
cp "$ROOT/LICENSES/webrtc-bsd-3.txt" "$ROOT/app/src/main/assets/licenses/webrtc-bsd-3.txt"
cp "$ROOT/LICENSES/webrtc-sdk-mit.txt" "$ROOT/app/src/main/assets/licenses/webrtc-sdk-mit.txt"
