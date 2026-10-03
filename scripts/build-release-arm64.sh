#!/usr/bin/env bash
# Baut die signierte Release-APK für arm64-v8a und gibt ihren Pfad aus.
# Voraussetzung: keystore.properties im Projektordner (siehe keystore.properties.example).
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f keystore.properties ]]; then
    echo "keystore.properties fehlt – Vorlage: keystore.properties.example" >&2
    exit 1
fi

./gradlew --console=plain clean :app:assembleRelease

apk=app/build/outputs/apk/release/app-release.apk
echo
echo "Release-APK (arm64-v8a): $apk"
