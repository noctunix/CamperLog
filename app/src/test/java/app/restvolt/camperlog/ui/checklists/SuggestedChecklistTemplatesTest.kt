package app.restvolt.camperlog.ui.checklists

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Prüft die vier vorgeschlagenen Checklisten-Vorlagen in beiden unterstützten Sprachen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestedChecklistTemplatesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS")
    fun englishSuggestionsHaveFourNonEmptyTemplatesWithPlausibleItemCounts() {
        val templates = suggestedChecklistTemplates(context.resources)

        assertEquals(listOf("Departure", "Arrival", "Winterizing", "Spring check"), templates.map { it.name })
        templates.forEach { template ->
            assertTrue("${template.name} has ${template.items.size} items", template.items.size in 6..10)
            assertTrue(template.items.all { it.isNotBlank() })
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "de-rDE")
    fun germanSuggestionsHaveFourNonEmptyTemplatesWithPlausibleItemCounts() {
        val templates = suggestedChecklistTemplates(context.resources)

        assertEquals(listOf("Abfahrt", "Ankunft", "Einwintern", "Frühjahrscheck"), templates.map { it.name })
        templates.forEach { template ->
            assertTrue("${template.name} has ${template.items.size} items", template.items.size in 6..10)
            assertTrue(template.items.all { it.isNotBlank() })
        }
    }

    @Test
    fun templatesHaveNoIdOrUuidYetAndWaitForSave() {
        val templates = suggestedChecklistTemplates(context.resources)

        assertTrue(templates.all { it.id == 0L && it.uuid.isEmpty() })
    }
}
