#!/usr/bin/env bash
#
# Builds every artifact this host can produce and bundles them into a single
# release ZIP under dist/.
#
#   ./build-scripts/package-release.sh [version]
#
# On Linux/macOS this produces the native desktop installer for the current OS
# (.deb / .dmg) plus the Android APK. The Windows .exe can only be produced on
# Windows - use build-scripts/package-release.ps1 there, or let the release
# workflow in .github/workflows/release.yml build both halves and merge them.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-$(sed -n 's/^mcgui\.version=//p' gradle.properties)}"
VERSION="${VERSION:-1.0.0}"

STAGE="$ROOT/dist/stage"
OUT="$ROOT/dist"
ZIP_NAME="uilabs-$VERSION.zip"

echo "==> UILabs $VERSION"
echo "==> Cleaning previous staging output"
rm -rf "$STAGE"
mkdir -p "$STAGE"/{desktop,android,docs,templates}

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

GRADLE_ARGS=(--no-daemon "-Pmcgui.version=$VERSION")

echo "==> Running checks"
./gradlew "${GRADLE_ARGS[@]}" validateProjects allTests

# The native installer needs host tooling that is not always present (dpkg-deb
# and fakeroot on Linux, WiX on Windows), so a failure here must not lose the
# rest of the bundle - the portable jar below is always produced.
echo "==> Building the native desktop installer"
if ! ./gradlew "${GRADLE_ARGS[@]}" :desktopApp:packageDistributionForCurrentOS; then
  echo "==> Native installer failed on this host; continuing with the portable jar"
fi

echo "==> Building the portable desktop jar"
./gradlew "${GRADLE_ARGS[@]}" :desktopApp:packageUberJarForCurrentOS

if [ -d "$ANDROID_HOME" ] || [ -f local.properties ]; then
  echo "==> Building the Android release APK"
  ./gradlew "${GRADLE_ARGS[@]}" :androidApp:assembleRelease :androidApp:bundleRelease
else
  echo "==> Skipping the APK: no Android SDK found (set ANDROID_HOME to build it)"
fi

# ---------------------------------------------------------------------------
# Collect
# ---------------------------------------------------------------------------

echo "==> Collecting artifacts"

# Native installers (.deb / .dmg / .exe / .msi, whichever this host made).
find desktopApp/build/compose/binaries -maxdepth 4 -type f \
  \( -name '*.exe' -o -name '*.msi' -o -name '*.deb' -o -name '*.dmg' -o -name '*.rpm' \) \
  -exec cp {} "$STAGE/desktop/" \; 2>/dev/null || true

find desktopApp/build/compose/jars -maxdepth 1 -type f -name '*.jar' \
  -exec cp {} "$STAGE/desktop/" \; 2>/dev/null || true

find androidApp/build/outputs/apk -type f -name '*.apk' \
  -exec cp {} "$STAGE/android/" \; 2>/dev/null || true
# The .aab is for uploading to Google Play; it is not installable on a device.
find androidApp/build/outputs/bundle -type f -name '*.aab' \
  -exec cp {} "$STAGE/android/" \; 2>/dev/null || true

cp -r templates/. "$STAGE/templates/"
cp -r docs/. "$STAGE/docs/"
cp README.md LICENSE "$STAGE/" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Source archive
# ---------------------------------------------------------------------------

echo "==> Archiving the source tree"
if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  # git archive gives a reproducible snapshot that honours .gitignore.
  git -C "$ROOT" archive --format=zip --prefix="source/" \
    -o "$STAGE/source.zip" HEAD
else
  echo "    (not a git checkout - falling back to a manual copy)"
  mkdir -p "$STAGE/source"
  rsync -a \
    --exclude '.git' --exclude 'build' --exclude '.gradle' --exclude 'dist' \
    --exclude '.idea' --exclude '.kotlin' \
    "$ROOT/" "$STAGE/source/"
fi

# ---------------------------------------------------------------------------
# Manifest and ZIP
# ---------------------------------------------------------------------------

{
  echo "UILabs $VERSION"
  echo "Built on $(date -u '+%Y-%m-%d %H:%M:%S UTC') from $(uname -s) $(uname -m)"
  echo
  echo "Contents:"
  echo "  desktop/    Desktop installers and the portable jar built on this host"
  echo "  android/    Android APK (sideload) and .aab (Google Play upload)"
  echo "  templates/  Bundled .mcgui templates and one full set of sample exports"
  echo "  docs/       Architecture, project format and export documentation"
  echo "  source.zip  Complete source tree, including the Gradle build"
  echo
  echo "Artifacts:"
  (cd "$STAGE" && find . -type f | sort | sed 's|^\./|  |')
} > "$STAGE/MANIFEST.txt"

echo "==> Writing $OUT/$ZIP_NAME"
rm -f "$OUT/$ZIP_NAME"
(cd "$STAGE" && zip -qr "$OUT/$ZIP_NAME" .)

echo
echo "Done: $OUT/$ZIP_NAME"
ls -lh "$OUT/$ZIP_NAME"
