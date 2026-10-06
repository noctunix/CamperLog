package app.restvolt.camperlog.ui.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Jede `implementation(libs.xxx)`-Abhängigkeit in `app/build.gradle.kts`, die mit in die APK
 * wandert, muss in [ThirdPartyLibraries] auftauchen - sonst wird eine neue Abhängigkeit beim
 * Versions-Bump vergessen. `debugImplementation`, `testImplementation` und `ksp(...)` sind
 * bewusst ausgenommen: Sie landen nicht im Release-Build.
 */
class ThirdPartyLibrariesTest {

    /** Welcher Eintrag in [ThirdPartyLibraries] eine Version-Catalog-Alias abdeckt. */
    private val aliasToEntry = mapOf(
        "androidx.core.ktx" to "AndroidX Core",
        "androidx.activity.compose" to "AndroidX Activity",
        "androidx.lifecycle.viewmodel.compose" to "AndroidX Lifecycle",
        "androidx.lifecycle.runtime.compose" to "AndroidX Lifecycle",
        "androidx.lifecycle.viewmodel.savedstate" to "AndroidX Lifecycle",
        "androidx.navigation.compose" to "AndroidX Navigation",
        "androidx.compose.bom" to "Jetpack Compose (UI, Material 3)",
        "androidx.compose.material3" to "Jetpack Compose (UI, Material 3)",
        "androidx.compose.ui.tooling.preview" to "Jetpack Compose (UI, Material 3)",
        "androidx.room.runtime" to "AndroidX Room",
        "kotlinx.serialization.json" to "kotlinx.serialization",
        "androidx.work.runtime.ktx" to "AndroidX WorkManager",
        "androidx.documentfile" to "AndroidX DocumentFile",
        "androidx.exifinterface" to "AndroidX ExifInterface",
    )

    @Test
    fun everyShippedImplementationAliasIsCoveredByAnEntry() {
        val entryNames = ThirdPartyLibraries.entries.map { it.name }.toSet()
        val aliases = shippedLibsAliases(File("build.gradle.kts"))

        assertTrue("No implementation(libs.xxx) aliases were found; is the parser broken?", aliases.isNotEmpty())

        val uncovered = aliases.filterNot { alias -> aliasToEntry[alias]?.let { it in entryNames } == true }
        assertTrue("Uncovered dependencies, add them to ThirdPartyLibraries or to the mapping above: $uncovered", uncovered.isEmpty())
    }

    @Test
    fun debugAndTestOnlyDependenciesAreExcluded() {
        val aliases = shippedLibsAliases(File("build.gradle.kts"))

        assertFalse(aliases.contains("junit"))
        assertFalse(aliases.contains("robolectric"))
        assertFalse(aliases.contains("androidx.compose.ui.tooling"))
        assertFalse(aliases.contains("androidx.compose.ui.test.manifest"))
        assertFalse(aliases.contains("androidx.room.compiler"))
    }
}

/** Extrahiert alle `libs.xxx`-Aliase aus `implementation(...)`-Zeilen (auch innerhalb `platform(...)`). */
private fun shippedLibsAliases(buildFile: File): List<String> {
    val implementationLine = Regex("""^\s*implementation\(""")
    val aliasToken = Regex("""libs\.([a-zA-Z0-9]+(?:\.[a-zA-Z0-9]+)*)""")
    return buildFile.readLines()
        .filter { implementationLine.containsMatchIn(it) }
        .flatMap { line -> aliasToken.findAll(line).map { it.groupValues[1] } }
}
