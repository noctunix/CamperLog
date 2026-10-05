#!/usr/bin/env bash
# Regenerate the fastlane screenshots in fastlane/metadata/android/en-US/images/phoneScreenshots/
# from sample data (Robolectric).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/fastlane/metadata/android/en-US/images/phoneScreenshots"
mkdir -p "$out"

cd "$root"
CAMPERLOG_SCREENSHOTS="$out" ./gradlew :app:testDebugUnitTest \
    --tests 'app.restvolt.camperlog.ui.ReadmeScreenshots' --rerun -q

# Shrink for the README (half of xxhdpi width) if ImageMagick is available.
if command -v magick >/dev/null; then
    for f in "$out"/*.png; do
        magick "$f" -resize 540x -strip "$f"
    done
fi
ls -l "$out"
