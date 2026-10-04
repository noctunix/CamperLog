# CamperLog

CamperLog ist eine Android-App für Touren, Kosten und Währungen. Entwickelt mit Kotlin, Jetpack Compose und Room.

## Entwicklung

Voraussetzungen: JDK 17, Android SDK (Plattform 37) und ein Android-Gerät oder Emulator für die App. Der Gradle Wrapper ist im Repository enthalten.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Pull Requests und Pushes auf `master` durchlaufen diese Prüfungen in [GitHub Actions](.github/workflows/ci.yml). Der Debug-Build ist keine veröffentlichte Release-Version.

## Versionen und Releases

Die einzige Versionsangabe steht als `appVersion` in `app/build.gradle.kts`. Androids `versionCode` wird daraus abgeleitet. Für jedes neue Release die Version erhöhen (SemVer, `MINOR` und `PATCH` jeweils kleiner als 100), die Änderungen committen und den **Commit auf `master`** mit `v<Version>` taggen. Danach den Branch und das Tag nach GitHub pushen:

```sh
git push github master
git tag -a v1.0.1 -m 'CamperLog 1.0.1' # Beispiel: nur wenn noch kein Tag existiert
git push github v1.0.1
```

[Der Release-Workflow](.github/workflows/release.yml) prüft Version, Tag und Branch, führt Tests und Lint aus und erstellt erst danach den GitHub-Release. Ohne Signing-Secrets enthält er ausschließlich die automatisch bereitgestellten Quellcode-Archive. Ein Tag ist unveränderlich zu behandeln: Für Korrekturen die Version erhöhen, keinen veröffentlichten Tag verschieben.

### Signierte APK (arm64)

Für lokale Builds `keystore.properties.example` lesen und den Keystore **dauerhaft und sicher sichern**. `scripts/build-release-arm64.sh --no-install` baut ohne Geräteinstallation eine signierte APK. Ein Schlüsselwechsel verhindert Updates bereits installierter Apps mit erhaltenen Daten.

Um bei künftigen GitHub-Releases eine installierbare APK zu veröffentlichen, in den Repository-Secrets unter **Settings → Secrets and variables → Actions** beide Werte hinterlegen:

- `RELEASE_KEYSTORE_BASE64`: Base64-Inhalt des dauerhaft gesicherten Release-Keystores.
- `RELEASE_PROPERTIES_BASE64`: Base64-Inhalt einer `keystore.properties` mit `storeFile=release.jks`, `storePassword`, `keyAlias` und `keyPassword` für genau diesen Keystore.

Beispiel auf Linux: `base64 -w0 camperlog-release.jks` bzw. `base64 -w0 keystore.properties` ausführen und die Ausgabe jeweils direkt als Secret hinterlegen. Die Properties-Datei muss für den GitHub-Build `storeFile=release.jks` enthalten. **Weder Keystore noch Passwörter einchecken.** Sind nur eines der Secrets oder ungültige Signing-Daten vorhanden, schlägt der Release-Workflow fehl, statt eine unsignierte APK zu veröffentlichen.

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE).
