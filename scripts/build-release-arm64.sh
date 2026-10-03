#!/usr/bin/env bash
# Baut die signierte Release-APK für arm64-v8a und installiert sie, wenn genau ein
# passendes Gerät angeschlossen ist (oder ANDROID_SERIAL eines auswählt).
# Voraussetzung: keystore.properties im Projektordner (siehe keystore.properties.example).
#
# Android Studio: Run-Konfiguration "Release APK (arm64)" (liegt in .run/) startet dieses
# Skript. Eine Gradle-Run-Konfiguration würde nur bauen; installieren kann nur das Skript.
#
#   scripts/build-release-arm64.sh               bauen und installieren
#   scripts/build-release-arm64.sh --no-install  nur bauen
set -euo pipefail

cd "$(dirname "$0")/.."

install=true
case "${1:-}" in
    "") ;;
    --no-install) install=false ;;
    *) echo "Unbekannte Option: $1 (erlaubt: --no-install)" >&2; exit 2 ;;
esac

if [[ ! -f keystore.properties ]]; then
    echo "keystore.properties fehlt – Anleitung in keystore.properties.example" >&2
    exit 1
fi

./gradlew --console=plain clean :app:assembleRelease

apk=app/build/outputs/apk/release/app-release.apk
size_mb=$(awk -v b="$(wc -c < "$apk")" 'BEGIN { printf "%.1f", b / 1048576 }')
version=$(sed -n 's/^val appVersion = "\(.*\)"$/\1/p' app/build.gradle.kts)
printf '\nRelease-APK %s (arm64-v8a): %s  %s MB\n' "$version" "$apk" "$size_mb"

# Eine Version soll genau einem Stand entsprechen: getaggter Commit ohne lokale Änderungen.
if ! git describe --exact-match --tags --match "v$version" HEAD > /dev/null 2>&1; then
    echo "Hinweis: Commit ist nicht als v$version getaggt – Version bumpen und taggen?"
elif [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    echo "Hinweis: Lokale Änderungen – die APK entspricht nicht genau v$version."
fi

[[ "$install" == true ]] || exit 0

# adb aus PATH, sonst aus dem SDK (ANDROID_HOME oder sdk.dir in local.properties).
adb=$(command -v adb || true)
if [[ -z "$adb" ]]; then
    sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    if [[ -z "$sdk" && -f local.properties ]]; then
        sdk=$(sed -n 's/^sdk\.dir=//p' local.properties | sed 's/\\:/:/g; s/\\\\/\\/g')
    fi
    [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]] && adb="$sdk/platform-tools/adb"
fi
if [[ -z "$adb" ]]; then
    echo "Hinweis: adb nicht gefunden, Installation übersprungen."
    exit 0
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    serial=$ANDROID_SERIAL
else
    # Ein fehlschlagendes "adb devices" (z. B. Server startet nicht) zählt als "kein Gerät".
    devices=$("$adb" devices 2>/dev/null | awk -F'\t' '$2 == "device" { print $1 }' || true)
    count=$(printf '%s' "$devices" | grep -c . || true)
    if [[ "$count" -eq 0 ]]; then
        echo "Hinweis: Kein Gerät angeschlossen, Installation übersprungen."
        exit 0
    elif [[ "$count" -gt 1 ]]; then
        echo "Hinweis: $count Geräte angeschlossen, Installation übersprungen. Gerät wählen mit:"
        echo "  ANDROID_SERIAL=<serial> $0"
        printf '%s\n' "$devices" | sed 's/^/  /'
        exit 0
    fi
    serial=$devices
fi

# Die APK enthält nur arm64-Code; x86-Emulatoren würden die Installation ablehnen.
abis=$("$adb" -s "$serial" shell getprop ro.product.cpu.abilist | tr -d '\r')
if [[ ",$abis," != *",arm64-v8a,"* ]]; then
    echo "Hinweis: Gerät $serial unterstützt kein arm64-v8a ($abis), Installation übersprungen."
    exit 0
fi

echo "Installiere auf $serial …"
if ! output=$("$adb" -s "$serial" install -r "$apk" 2>&1); then
    echo "$output" >&2
    if [[ "$output" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
        # Bewusst kein automatisches Deinstallieren: dabei gingen alle Touren verloren.
        echo >&2
        echo "Auf dem Gerät ist CamperLog mit einem anderen Schlüssel installiert (z. B. ein" >&2
        echo "Debug-Build aus Android Studio). Ein Update ist so nicht möglich. Erst Touren" >&2
        echo "exportieren, dann die App deinstallieren und das Skript erneut starten." >&2
    elif [[ "$output" == *INSTALL_FAILED_VERSION_DOWNGRADE* ]]; then
        echo >&2
        echo "Auf dem Gerät ist eine neuere Version installiert. Version $version in" >&2
        echo "app/build.gradle.kts (appVersion) höher setzen." >&2
    fi
    exit 1
fi
echo "$output" | tail -1
