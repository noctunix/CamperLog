package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.Checklist
import app.restvolt.camperlog.domain.ChecklistItem
import app.restvolt.camperlog.domain.ChecklistTemplate
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * Checklisten-Vorlagen und Checklisten im JSON-Teil einer Sicherung: Rundgang und Validierung
 * (siehe `BackupTest` für den Rest des Formats, `BackupDiaryEntriesTest` für das analoge Muster).
 */
class BackupChecklistsTest {

    private val nok = Currency.getInstance("NOK")
    private val vehicleUuid = "2b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val tourUuid = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val templateUuid = "9b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val checklistUuid = "8b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"

    private fun vehicle() = Vehicle(uuid = vehicleUuid, name = "Bluebird", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun tour() = Tour(
        uuid = tourUuid,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 10),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 2000,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun template() = ChecklistTemplate(
        uuid = templateUuid,
        name = "Abfahrt",
        items = listOf("Dachluken schließen", "Trittstufe einfahren"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.parse("2026-07-04T20:00:00Z"),
    )

    private fun checklist(tourUuid: String? = this.tourUuid) = Checklist(
        uuid = checklistUuid,
        vehicleId = 0,
        title = "Abfahrt",
        items = listOf(ChecklistItem("Dachluken schließen"), ChecklistItem("Trittstufe einfahren", checked = true)),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.parse("2026-07-04T20:00:00Z"),
    ).let { BackupChecklist(it, vehicleUuid, tourUuid) }

    private fun backup(
        checklistTemplates: List<ChecklistTemplate> = listOf(template()),
        checklists: List<BackupChecklist> = listOf(checklist()),
    ) = Backup(
        exportedAt = Instant.parse("2026-10-06T12:00:00Z"),
        mainCurrency = nok,
        rates = emptyList(),
        tours = listOf(tour()),
        tourVehicleUuid = mapOf(tourUuid to vehicleUuid),
        vehicles = listOf(BackupVehicle(vehicle(), emptyList(), emptyList())),
        checklistTemplates = checklistTemplates,
        checklists = checklists,
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup
    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    @Test
    fun roundTrip_keepsChecklistTemplateWithItsItems() {
        val decoded = success(encodeBackup(backup()))

        val decodedTemplate = decoded.checklistTemplates.single()
        assertEquals("Abfahrt", decodedTemplate.name)
        assertEquals(listOf("Dachluken schließen", "Trittstufe einfahren"), decodedTemplate.items)
        assertEquals(Instant.parse("2026-07-04T20:00:00Z"), decodedTemplate.updatedAt)
    }

    @Test
    fun roundTrip_keepsChecklistWithVehicleAndTourUuidAndCheckedState() {
        val decoded = success(encodeBackup(backup()))

        val decodedChecklist = decoded.checklists.single()
        assertEquals("Abfahrt", decodedChecklist.checklist.title)
        assertEquals(
            listOf(ChecklistItem("Dachluken schließen"), ChecklistItem("Trittstufe einfahren", checked = true)),
            decodedChecklist.checklist.items,
        )
        assertEquals(vehicleUuid, decodedChecklist.vehicleUuid)
        assertEquals(tourUuid, decodedChecklist.tourUuid)
    }

    @Test
    fun roundTrip_keepsChecklistWithoutATour() {
        val decoded = success(encodeBackup(backup(checklists = listOf(checklist(tourUuid = null)))))

        assertEquals(null, decoded.checklists.single().tourUuid)
    }

    @Test
    fun missingChecklistTemplatesAndChecklists_decodeAsEmpty_forOlderBackups() {
        // Ein Backup vor Formatversion 9 kennt diese Felder gar nicht.
        val withoutFields = Json.parseToJsonElement(encodeBackup(backup(emptyList(), emptyList())))
            .jsonObject.filterKeys { it != "checklistTemplates" && it != "checklists" }
        val text = JsonObject(withoutFields).toString()

        val decoded = success(text)

        assertEquals(emptyList<ChecklistTemplate>(), decoded.checklistTemplates)
        assertEquals(emptyList<BackupChecklist>(), decoded.checklists)
    }

    @Test
    fun checklistWithUnknownVehicleUuid_isRejected() {
        val broken = backup(checklists = listOf(checklist().copy(vehicleUuid = "ffffffff-ffff-4fff-8fff-ffffffffffff")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun checklistWithUnknownTourUuid_isRejected() {
        val broken = backup(checklists = listOf(checklist(tourUuid = "ffffffff-ffff-4fff-8fff-ffffffffffff")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateChecklistUuid_isRejected() {
        val broken = backup(checklists = listOf(checklist(), checklist()))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateChecklistTemplateUuid_isRejected() {
        val broken = backup(checklistTemplates = listOf(template(), template()))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistTemplateNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun blankChecklistTemplateName_isRejected() {
        val broken = backup(checklistTemplates = listOf(template().copy(name = "   ")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistTemplateNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun blankChecklistTemplateItem_isRejected() {
        val broken = backup(checklistTemplates = listOf(template().copy(items = listOf("   "))))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistTemplateNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun tooManyChecklistTemplateItems_isRejected() {
        val broken = backup(checklistTemplates = listOf(template().copy(items = List(MAX_CHECKLIST_TEMPLATE_ITEMS + 1) { "Punkt $it" })))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistTemplateNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun blankChecklistItemText_isRejected() {
        val brokenChecklist = checklist().checklist.copy(items = listOf(ChecklistItem("   "))).let { BackupChecklist(it, vehicleUuid, tourUuid) }
        val broken = backup(checklists = listOf(brokenChecklist))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, checklistNumber = 1), failure(encodeBackup(broken)))
    }
}
