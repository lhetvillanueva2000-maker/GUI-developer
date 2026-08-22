#!/usr/bin/env bash
#
# Builds `assets/icon/app-icon.icns` from `assets/icon/icon-1024.png`.
#
#   ./build-scripts/make-icns.sh
#
# macOS only: `sips` and `iconutil` both ship with the OS and neither has a
# portable equivalent, which is why the .icns is generated on the macOS runner
# rather than committed.  The desktop build picks the file up automatically if
# it exists and falls back to the default Java icon if it does not, so running
# this is optional everywhere except when producing a release .dmg.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/assets/icon/icon-1024.png"
TARGET="$ROOT/assets/icon/app-icon.icns"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "make-icns.sh: not macOS - nothing to do (the build falls back to the default icon)."
  exit 0
fi

if [[ ! -f "$SOURCE" ]]; then
  echo "make-icns.sh: $SOURCE is missing." >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
ICONSET="$WORK/app-icon.iconset"
mkdir -p "$ICONSET"

# The exact set of names `iconutil` expects; anything else is ignored silently,
# which is a good way to ship an icon that only renders at one size.
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$SOURCE" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
  retina=$((size * 2))
  sips -z "$retina" "$retina" "$SOURCE" --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
done

iconutil --convert icns "$ICONSET" --output "$TARGET"
echo "make-icns.sh: wrote $TARGET ($(du -h "$TARGET" | cut -f1))"
