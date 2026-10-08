#!/usr/bin/env bash
# Regenerate the README and English/German F-Droid screenshots from sample data (Robolectric).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/fastlane/metadata/android/en-US/images/phoneScreenshots"
out_de="$root/fastlane/metadata/android/de-DE/images/phoneScreenshots"
mkdir -p "$out"
mkdir -p "$out_de"

cd "$root"
CAMPERLOG_SCREENSHOTS="$out" CAMPERLOG_SCREENSHOTS_DE="$out_de" ./gradlew :app:testDebugUnitTest \
    --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
    --tests 'app.restvolt.camperlog.ui.ReadmeScreenshots' --rerun --no-configuration-cache -q

# Shrink for the README (half of xxhdpi width) if ImageMagick is available.
if command -v magick >/dev/null; then
    for f in "$out"/*.png; do
        magick "$f" -resize 540x -strip "$f"
    done
fi
ls -l "$out"
ls -l "$out_de"
