# CamperLog

CamperLog is an Android app for recording trips, expenses, and currencies. It is built with Kotlin, Jetpack Compose, and Room. The app uses English by default and German on devices configured for German. CSV export keeps its existing column names and values for compatibility with earlier exports.

## Development

Requirements: JDK 17, Android SDK (platform 37), and an Android device or emulator to run the app. The Gradle wrapper is included.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Pushes to `master` and pull requests run these checks in [GitHub Actions](.github/workflows/ci.yml). Debug APKs are not published as releases.

## Versions and releases

The sole version declaration is `appVersion` in `app/build.gradle.kts`; Android's `versionCode` is derived from it. For each release, increment the version (SemVer; `MINOR` and `PATCH` must each be below 100), commit the changes, and tag the **commit on `master`** with `v<version>`. Push the branch before the tag:

```sh
git push github master
git tag -a v1.0.4 -m 'CamperLog 1.0.4' # Example: after bumping to the next unused version
git push github v1.0.4
```

The [release workflow](.github/workflows/release.yml) verifies the version, tag, and branch, runs tests and lint, builds a signed arm64 APK, verifies its signature, and publishes it with a SHA-256 checksum. A release fails if signing secrets are missing or invalid. Treat published tags as immutable: increment the version for fixes rather than moving a tag.

### Signed APK (arm64)

For local builds, follow `keystore.properties.example` and **keep a permanent, secure backup of the keystore**. `scripts/build-release-arm64.sh --no-install` builds a signed APK without installing it. Replacing the signing key prevents updating existing installations while retaining their data.

To publish an installable APK, configure both repository secrets under **Settings → Secrets and variables → Actions**:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded content of the permanently backed-up release keystore.
- `RELEASE_PROPERTIES_BASE64`: Base64-encoded `keystore.properties` with `storeFile=release.jks`, `storePassword`, `keyAlias`, and `keyPassword` for that keystore.

On Linux, use `base64 -w0 camperlog-release.jks` and `base64 -w0 keystore.properties`, then enter the respective outputs directly as secrets. The properties file encoded for GitHub **must** contain `storeFile=release.jks`. Never commit the keystore or passwords.

## License

Apache License 2.0; see [LICENSE](LICENSE).
