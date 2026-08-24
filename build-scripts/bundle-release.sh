#!/usr/bin/env bash
#
# Merges the per-platform CI artifacts into the single release ZIP promised by
# the README: the Windows .exe, the Android .apk, the full source tree, the
# build scripts, the templates and the documentation.
#
#   ./build-scripts/bundle-release.sh <artifacts-dir> <version> [output-dir]
#
# <artifacts-dir> is the directory GitHub Actions downloaded every job's
# artifacts into, so it looks like:
#
#   artifacts/
#     windows-desktop/       UILabs-1.0.0.exe, ...msi
#     macos-desktop-aarch64/ UILabs-1.0.0-macos-aarch64.dmg
#     macos-desktop-x64/     UILabs-1.0.0-macos-x64.dmg
#     linux-and-android/     androidApp-release.apk / .aab, ...amd64.deb
#     portable-jar/          UILabs-<os>-<arch>-1.0.0.jar
#
# Anything missing is reported and skipped rather than failing the build, so a
# partial run still produces a usable archive.

set -euo pipefail

ARTIFACTS="${1:?usage: bundle-release.sh <artifacts-dir> <version> [output-dir]}"
VERSION="${2:?usage: bundle-release.sh <artifacts-dir> <version> [output-dir]}"
OUT_DIR="${3:-dist}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

ZIP_NAME="uilabs-$VERSION.zip"
mkdir -p "$OUT_DIR" "$STAGE"/{desktop,android,docs,templates,build-scripts}

echo "==> Bundling UILabs $VERSION"

collect() {
  local label="$1" pattern="$2" destination="$3"
  local found=0
  while IFS= read -r -d '' file; do
    cp "$file" "$destination/"
    echo "    + $(basename "$file")"
    found=1
  done < <(find "$ARTIFACTS" -type f -name "$pattern" -print0 2>/dev/null)
  if [ "$found" -eq 0 ]; then
    echo "    ! no $label found (looked for '$pattern')"
  fi
}

echo "==> Desktop"
collect "Windows installer" '*.exe' "$STAGE/desktop"
collect "Windows package" '*.msi' "$STAGE/desktop"
collect "macOS disk image" '*.dmg' "$STAGE/desktop"
collect "macOS package" '*.pkg' "$STAGE/desktop"
collect "Linux package" '*.deb' "$STAGE/desktop"
collect "portable jar" '*.jar' "$STAGE/desktop"

echo "==> Android"
collect "APK" '*.apk' "$STAGE/android"
collect "app bundle" '*.aab' "$STAGE/android"

echo "==> Project files"
cp -r templates/. "$STAGE/templates/"
cp -r docs/. "$STAGE/docs/"
cp -r build-scripts/. "$STAGE/build-scripts/"
cp README.md "$STAGE/"
[ -f LICENSE ] && cp LICENSE "$STAGE/"

echo "==> Source archive"
git archive --format=zip --prefix="source/" -o "$STAGE/source.zip" HEAD
echo "    + source.zip ($(du -h "$STAGE/source.zip" | cut -f1))"

{
  echo "UILabs $VERSION"
  echo "Bundled $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
  echo "Commit: $(git rev-parse HEAD)"
  echo
  echo "Contents:"
  echo "  desktop/       Windows (.exe/.msi), macOS (.dmg/.pkg), Linux .deb, portable .jar"
  echo "  android/       Android APK (sideload) and .aab (Google Play upload)"
  echo "  templates/     Bundled .mcgui templates plus one full set of sample exports"
  echo "  docs/          Architecture, project format and export documentation"
  echo "  build-scripts/ The scripts that produced this archive"
  echo "  source.zip     Complete source tree, including the Gradle build"
  echo
  echo "Files:"
  (cd "$STAGE" && find . -type f | sort | sed 's|^\./|  |')
  echo
  echo "SHA-256:"
  (cd "$STAGE" && find . -type f ! -name MANIFEST.txt -exec sha256sum {} \; | sort -k2)
} > "$STAGE/MANIFEST.txt"

rm -f "$OUT_DIR/$ZIP_NAME"
(cd "$STAGE" && zip -qr "$ROOT/$OUT_DIR/$ZIP_NAME" .)

echo
echo "Done: $OUT_DIR/$ZIP_NAME"
ls -lh "$OUT_DIR/$ZIP_NAME"
