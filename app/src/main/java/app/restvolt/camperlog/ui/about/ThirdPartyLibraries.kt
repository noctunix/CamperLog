package app.restvolt.camperlog.ui.about

/** Ein Open-Source-Baustein im Abschnitt „Drittanbieter-Bibliotheken“ des Über-Bildschirms. */
data class ThirdPartyLibrary(val name: String, val license: String, val url: String)

/**
 * Statische Attributionsliste; ohne Versionsnummern, da sie bei jeder Abhängigkeits-Aktualisierung
 * in `app/build.gradle.kts` sonst nachgepflegt werden müssten, Lizenz und URL sich aber nicht ändern.
 * Siehe `ThirdPartyLibrariesTest` für den Abgleich gegen die tatsächlichen Abhängigkeiten.
 */
object ThirdPartyLibraries {
    val entries: List<ThirdPartyLibrary> = listOf(
        ThirdPartyLibrary(
            name = "AndroidX Core",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/core",
        ),
        ThirdPartyLibrary(
            name = "AndroidX Activity",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/activity",
        ),
        ThirdPartyLibrary(
            name = "AndroidX Lifecycle",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        ),
        ThirdPartyLibrary(
            name = "AndroidX Navigation",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/navigation",
        ),
        ThirdPartyLibrary(
            name = "Jetpack Compose (UI, Material 3)",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/compose",
        ),
        ThirdPartyLibrary(
            name = "AndroidX Room",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
        ),
        ThirdPartyLibrary(
            name = "Kotlin standard library",
            license = "Apache-2.0",
            url = "https://kotlinlang.org",
        ),
        ThirdPartyLibrary(
            name = "kotlinx.coroutines",
            license = "Apache-2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        ThirdPartyLibrary(
            name = "kotlinx.serialization",
            license = "Apache-2.0",
            url = "https://github.com/Kotlin/kotlinx.serialization",
        ),
        ThirdPartyLibrary(
            name = "Material Symbols",
            license = "Apache-2.0",
            url = "https://fonts.google.com/icons",
        ),
        ThirdPartyLibrary(
            name = "AndroidX WorkManager",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/work",
        ),
        ThirdPartyLibrary(
            name = "AndroidX DocumentFile",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/documentfile",
        ),
        ThirdPartyLibrary(
            name = "AndroidX ExifInterface",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/exifinterface",
        ),
    )
}
