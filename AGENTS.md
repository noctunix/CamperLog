# CamperLog

- Android app in Kotlin with Jetpack Compose, Room and Gradle. Production code is in `app/src/main/java/app/restvolt/camperlog/`; JVM/Robolectric tests are in `app/src/test/java/app/restvolt/camperlog/`.
- Start at `MainActivity.kt` and `ui/Navigation.kt` for screen flows; `domain/` owns validation and currency calculations, `data/` owns Room storage, and `share/` owns exports.
- Keep user-facing text in `app/src/main/res/values/strings.xml`; preserve locale-aware amount parsing, currency precision, CSV quoting and formula-injection protection.
- Run focused tests with `./gradlew :app:testDebugUnitTest --tests 'fully.qualified.TestClass'`; run the full suite with `./gradlew :app:testDebugUnitTest`. Room schema changes require checking the exported schemas and migration tests.
- The signed release build uses `scripts/build-release-arm64.sh` and local signing credentials; don't commit credentials or generated build outputs.
