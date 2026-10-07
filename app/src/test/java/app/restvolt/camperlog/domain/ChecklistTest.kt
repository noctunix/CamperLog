package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChecklistTest {

    private fun template() = ChecklistTemplate(
        id = 1,
        uuid = "template-1",
        name = "Abfahrt",
        items = listOf("Dachluken schließen", "Trittstufe einfahren"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun startChecklistCopiesTheTemplateItemsUnchecked() {
        val checklist = startChecklist(template(), vehicleId = 5, tourId = 9)

        assertEquals(5L, checklist.vehicleId)
        assertEquals(9L, checklist.tourId)
        assertEquals("Abfahrt", checklist.title)
        assertEquals(
            listOf(ChecklistItem("Dachluken schließen"), ChecklistItem("Trittstufe einfahren")),
            checklist.items,
        )
        assertTrue(checklist.items.none { it.checked })
    }

    @Test
    fun startChecklistWithoutATourLeavesTourIdNull() {
        val checklist = startChecklist(template(), vehicleId = 5)
        assertEquals(null, checklist.tourId)
    }

    @Test
    fun laterTemplateEditsDoNotAffectAnAlreadyStartedChecklist() {
        val checklist = startChecklist(template(), vehicleId = 5)
        val editedTemplate = template().copy(items = listOf("Ganz andere Punkte"))

        // Der Start kopiert die Punkte; eine erneut mit der bearbeiteten Vorlage gestartete Checkliste
        // zeigt die neuen Punkte, die zuerst gestartete bleibt unverändert.
        assertEquals(listOf("Dachluken schließen", "Trittstufe einfahren"), checklist.items.map { it.text })
        val secondChecklist = startChecklist(editedTemplate, vehicleId = 5)
        assertEquals(listOf("Ganz andere Punkte"), secondChecklist.items.map { it.text })
    }

    @Test
    fun checkedCountCountsOnlyCheckedItems() {
        val checklist = checklist(ChecklistItem("A", checked = true), ChecklistItem("B", checked = false))
        assertEquals(1, checklist.checkedCount)
    }

    @Test
    fun isCompleteRequiresAllItemsCheckedAndAtLeastOneItem() {
        assertFalse(checklist().isComplete)
        assertFalse(checklist(ChecklistItem("A", checked = true), ChecklistItem("B", checked = false)).isComplete)
        assertTrue(checklist(ChecklistItem("A", checked = true), ChecklistItem("B", checked = true)).isComplete)
    }

    private fun checklist(vararg items: ChecklistItem) = Checklist(
        vehicleId = 1,
        title = "Abfahrt",
        items = items.toList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
