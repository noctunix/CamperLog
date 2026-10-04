package app.restvolt.camperlog.ui.data

import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun show(existingTours: Int = 3) {
        val backup = Backup(Instant.parse("2026-10-04T12:00:00Z"), Currency.getInstance("NOK"), emptyList(), emptyList())
        compose.setContent {
            CamperLogTheme {
                ImportDialog(PendingImport(backup, existingTours), onImport = { chosen += it }, onCancel = { cancelled = true })
            }
        }
    }

    @Test
    fun preview_showsContents_andMergesByDefault() {
        show()
        compose.onNodeWithText("Sicherung vom 04.10.2026 mit 0 Touren und 0 Wechselkursen. Hauptwährung: NOK.").assertExists()

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

        compose.onNodeWithText("Ersetzen").performClick()

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
}
