package app.restvolt.camperlog.ui.data

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.ui.theme.CamperLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class ImportDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<ImportMode>()
    private var cancelled = false

    private fun show(existingTours: Int = 3, running: Boolean = false, failed: Boolean = false) {
        val backup = Backup(Instant.parse("2026-10-04T12:00:00Z"), Currency.getInstance("NOK"), emptyList(), emptyList())
        compose.setContent {
            CamperLogTheme {
                ImportDialog(PendingImport(backup, existingTours, running, failed), onImport = { chosen += it }, onCancel = { cancelled = true })
            }
        }
    }

    @Test
    fun preview_showsContents_andMergesByDefault() {
        show()
        compose.onNodeWithText("Sicherung vom 04.10.2026, ", substring = true).assertExists()
        compose.onNodeWithText(
            "mit 0 Touren, 0 Fahrzeuge, 0 Stationen, 0 Reparaturen, 0 Bordbuch-Einträge, 0 Wechselkursen und 0 Fahrzeugdokumente. Hauptwährung: NOK.",
            substring = true,
        ).assertExists()

        compose.onNodeWithText("Einspielen").performClick()

        assertEquals(listOf(ImportMode.MERGE), chosen)
    }

    @Test
    fun replace_needsSecondConfirmation() {
        show(existingTours = 3)
        compose.onNodeWithText("Alles ersetzen").performClick()
        compose.onNodeWithText("Einspielen").performClick()

        assertTrue(chosen.isEmpty())
        compose.onNodeWithText("Deine 3 gespeicherten Touren", substring = true).assertExists()

        compose.onNodeWithText("Alles ersetzen").performClick()

        assertEquals(listOf(ImportMode.REPLACE), chosen)
    }

    @Test
    fun replaceConfirmation_cancelReturnsToPreview() {
        show()
        compose.onNodeWithText("Alles ersetzen").performClick()
        compose.onNodeWithText("Einspielen").performClick()

        compose.onNodeWithText("Abbrechen").performClick()

        compose.onNodeWithText("Sicherung einspielen?").assertExists()
        assertTrue(chosen.isEmpty())
        assertTrue(!cancelled)
    }

    @Test
    fun running_locksDialogAndShowsProgress() {
        show(running = true)

        compose.onNodeWithText("Sicherung wird eingespielt …").assertExists()
        compose.onNodeWithText("Einspielen").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").assertIsNotEnabled()
    }

    @Test
    fun failed_showsErrorAndAllowsRetry() {
        show(failed = true)

        compose.onNodeWithText("Einspielen fehlgeschlagen. Es wurde nichts geändert.").assertExists()
        compose.onNodeWithText("Einspielen").performClick()

        assertEquals(listOf(ImportMode.MERGE), chosen)
    }
}
