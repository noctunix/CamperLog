package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChecklistTemplateInputTest {

    private val valid = ChecklistTemplateInput(name = "Abfahrt", items = listOf("Dachluken schließen", "Trittstufe einfahren"))

    @Test
    fun validInputHasNoErrors() {
        assertTrue(valid.validate().isEmpty())
    }

    @Test
    fun nameIsRequired() {
        assertEquals(ChecklistTemplateError.REQUIRED, valid.copy(name = "").validate()[ChecklistTemplateField.NAME])
        assertEquals(ChecklistTemplateError.REQUIRED, valid.copy(name = "   ").validate()[ChecklistTemplateField.NAME])
    }

    @Test
    fun emptyItemsAreNotRequired() {
        assertTrue(valid.copy(items = emptyList()).validate().isEmpty())
    }

    @Test
    fun nameAndItemsAreTrimmed() {
        val template = ChecklistTemplateInput(name = "  Abfahrt  ", items = listOf("  Dachluken schließen  ")).toTemplate(null)
        assertEquals("Abfahrt", template.name)
        assertEquals(listOf("Dachluken schließen"), template.items)
    }

    @Test
    fun blankItemsAreRemovedOnSave() {
        val template = valid.copy(items = listOf("Dachluken schließen", "", "   ", "Trittstufe einfahren")).toTemplate(null)
        assertEquals(listOf("Dachluken schließen", "Trittstufe einfahren"), template.items)
    }

    @Test
    fun toTemplateKeepsIdUuidAndTimestampsOfOriginal() {
        val original = ChecklistTemplate(
            id = 7,
            uuid = "template-7",
            name = "Alt",
            items = listOf("Alter Punkt"),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val edited = valid.toTemplate(original)
        assertEquals(7L, edited.id)
        assertEquals("template-7", edited.uuid)
        assertEquals(Instant.EPOCH, edited.createdAt)
        assertEquals(Instant.EPOCH, edited.updatedAt)
        assertEquals("Abfahrt", edited.name)
    }

    @Test
    fun roundTripThroughInputIsLossless() {
        val template = valid.toTemplate(null)
        assertEquals(template, template.toInput().toTemplate(template))
    }
}
