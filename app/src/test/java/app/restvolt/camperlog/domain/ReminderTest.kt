package app.restvolt.camperlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReminderTest {

    private val today = LocalDate.of(2026, 10, 5)

    private fun vehicle(
        nextInspectionDate: LocalDate? = null,
        nextGasCheckDate: LocalDate? = null,
        nextLeakTestDate: LocalDate? = null,
        lastOilChangeDate: LocalDate? = null,
        saleDate: LocalDate? = null,
    ) = Vehicle(
        nextInspectionDate = nextInspectionDate,
        nextGasCheckDate = nextGasCheckDate,
        nextLeakTestDate = nextLeakTestDate,
        lastOilChangeDate = lastOilChangeDate,
        saleDate = saleDate,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun includesDueDateExactlyAtTheLeadWindowBoundary() {
        val dueAtBoundary = vehicle(nextInspectionDate = today.plusDays(30))
        assertEquals(
            listOf(Reminder(ReminderKind.INSPECTION, today.plusDays(30), overdue = false)),
            dueReminders(dueAtBoundary, today, leadDays = 30, oilIntervalMonths = 12),
        )

        val justOutside = vehicle(nextInspectionDate = today.plusDays(31))
        assertEquals(emptyList<Reminder>(), dueReminders(justOutside, today, leadDays = 30, oilIntervalMonths = 12))
    }

    @Test
    fun overdueIsTrueOnlyBeforeToday() {
        val dueToday = vehicle(nextGasCheckDate = today)
        assertEquals(false, dueReminders(dueToday, today, leadDays = 30, oilIntervalMonths = 12).single().overdue)

        val dueYesterday = vehicle(nextGasCheckDate = today.minusDays(1))
        assertEquals(true, dueReminders(dueYesterday, today, leadDays = 30, oilIntervalMonths = 12).single().overdue)
    }

    @Test
    fun leakTestUsesTheSameLeadWindowAndOverdueLogicAsGasCheck() {
        val dueAtBoundary = vehicle(nextLeakTestDate = today.plusDays(30))
        assertEquals(
            listOf(Reminder(ReminderKind.LEAK_TEST, today.plusDays(30), overdue = false)),
            dueReminders(dueAtBoundary, today, leadDays = 30, oilIntervalMonths = 12),
        )

        val justOutside = vehicle(nextLeakTestDate = today.plusDays(31))
        assertEquals(emptyList<Reminder>(), dueReminders(justOutside, today, leadDays = 30, oilIntervalMonths = 12))

        val dueToday = vehicle(nextLeakTestDate = today)
        assertEquals(false, dueReminders(dueToday, today, leadDays = 30, oilIntervalMonths = 12).single().overdue)

        val dueYesterday = vehicle(nextLeakTestDate = today.minusDays(1))
        assertEquals(true, dueReminders(dueYesterday, today, leadDays = 30, oilIntervalMonths = 12).single().overdue)
    }

    @Test
    fun oilChangeIsDueOneIntervalAfterTheLastChange() {
        val vehicle = vehicle(lastOilChangeDate = today.minusMonths(12).plusDays(5))
        val reminders = dueReminders(vehicle, today, leadDays = 30, oilIntervalMonths = 12)
        assertEquals(listOf(Reminder(ReminderKind.OIL_CHANGE, today.plusDays(5), overdue = false)), reminders)

        val overdueVehicle = vehicle(lastOilChangeDate = today.minusMonths(13))
        assertEquals(true, dueReminders(overdueVehicle, today, leadDays = 30, oilIntervalMonths = 12).single().overdue)
    }

    @Test
    fun soldVehiclesHaveNoReminders() {
        val sold = vehicle(
            nextInspectionDate = today.minusDays(1),
            nextGasCheckDate = today.minusDays(1),
            nextLeakTestDate = today.minusDays(1),
            lastOilChangeDate = today.minusMonths(13),
            saleDate = today.minusDays(1),
        )
        assertEquals(emptyList<Reminder>(), dueReminders(sold, today, leadDays = 30, oilIntervalMonths = 12))
    }

    @Test
    fun nullDatesProduceNoReminderForThatKind() {
        assertEquals(emptyList<Reminder>(), dueReminders(vehicle(), today, leadDays = 30, oilIntervalMonths = 12))
    }

    @Test
    fun combinesAllDueKinds() {
        val vehicle = vehicle(
            nextInspectionDate = today.plusDays(10),
            nextGasCheckDate = today.minusDays(2),
            nextLeakTestDate = today.plusDays(5),
            lastOilChangeDate = today.minusMonths(12),
        )
        val reminders = dueReminders(vehicle, today, leadDays = 30, oilIntervalMonths = 12)
        assertEquals(
            listOf(
                Reminder(ReminderKind.INSPECTION, today.plusDays(10), overdue = false),
                Reminder(ReminderKind.GAS_CHECK, today.minusDays(2), overdue = true),
                Reminder(ReminderKind.LEAK_TEST, today.plusDays(5), overdue = false),
                Reminder(ReminderKind.OIL_CHANGE, today, overdue = false),
            ),
            reminders,
        )
    }

    private fun document(id: Long = 1, title: String = "Fahrzeugschein", expiryDate: LocalDate?) = VehicleDocument(
        id = id,
        vehicleId = 1,
        kind = DocumentKind.REGISTRATION,
        title = title,
        expiryDate = expiryDate,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun documentReminders_includesDueDateExactlyAtTheLeadWindowBoundary() {
        val dueAtBoundary = document(expiryDate = today.plusDays(30))
        assertEquals(
            listOf(Reminder(ReminderKind.DOCUMENT_EXPIRY, today.plusDays(30), overdue = false, label = "Fahrzeugschein", documentId = 1)),
            documentReminders(listOf(dueAtBoundary), today, leadDays = 30),
        )

        val justOutside = document(expiryDate = today.plusDays(31))
        assertEquals(emptyList<Reminder>(), documentReminders(listOf(justOutside), today, leadDays = 30))
    }

    @Test
    fun documentReminders_overdueIsTrueOnlyBeforeToday() {
        val dueToday = document(expiryDate = today)
        assertEquals(false, documentReminders(listOf(dueToday), today, leadDays = 30).single().overdue)

        val overdue = document(expiryDate = today.minusDays(1))
        assertEquals(true, documentReminders(listOf(overdue), today, leadDays = 30).single().overdue)
    }

    @Test
    fun documentReminders_documentsWithoutExpiryDateProduceNoReminder() {
        assertEquals(emptyList<Reminder>(), documentReminders(listOf(document(expiryDate = null)), today, leadDays = 30))
    }

    @Test
    fun documentReminders_eachDueDocumentIsItsOwnReminderWithItsTitleAsLabel() {
        val documents = listOf(
            document(id = 1, title = "Fahrzeugschein", expiryDate = today.plusDays(5)),
            document(id = 2, title = "Versicherung", expiryDate = today.minusDays(1)),
        )

        val reminders = documentReminders(documents, today, leadDays = 30)

        assertEquals(
            listOf(
                Reminder(ReminderKind.DOCUMENT_EXPIRY, today.plusDays(5), overdue = false, label = "Fahrzeugschein", documentId = 1),
                Reminder(ReminderKind.DOCUMENT_EXPIRY, today.minusDays(1), overdue = true, label = "Versicherung", documentId = 2),
            ),
            reminders,
        )
    }
}
