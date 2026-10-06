package app.restvolt.camperlog.domain

import java.time.LocalDate

/**
 * Art der Erinnerung. [DOCUMENT_EXPIRY] kommt von einem ablaufenden Fahrzeugdokument statt einem festen
 * Fahrzeugfeld; es kann mehrere davon gleichzeitig geben, daher wird ihr Zustellungs-Zustand anders als
 * bei den übrigen Arten nicht über [ReminderNotificationState] je Fahrzeug und Art, sondern über
 * `DocumentReminderNotificationStore` je Dokument-id verfolgt.
 */
enum class ReminderKind { INSPECTION, GAS_CHECK, LEAK_TEST, OIL_CHANGE, DOCUMENT_EXPIRY }

/**
 * Fällige Erinnerung; [overdue], wenn [dueDate] bereits vor dem Bezugstag liegt. [label] und
 * [documentId] sind nur bei [ReminderKind.DOCUMENT_EXPIRY] gesetzt: [label] trägt den Dokumenttitel
 * zur Anzeige statt eines festen, artbezogenen Textes, [documentId] identifiziert das Dokument.
 */
data class Reminder(val kind: ReminderKind, val dueDate: LocalDate, val overdue: Boolean, val label: String? = null, val documentId: Long? = null)

/**
 * Liefert die fälligen Erinnerungen eines Fahrzeugs zum Zeitpunkt [today].
 *
 * Eine Erinnerung ist enthalten, wenn ihr Fälligkeitsdatum höchstens [leadDays] Tage in der
 * Zukunft liegt. Der Ölwechsel ist [oilIntervalMonths] Monate nach [Vehicle.lastOilChangeDate]
 * fällig. Verkaufte Fahrzeuge und Felder ohne Datum liefern keine Erinnerung.
 */
fun dueReminders(vehicle: Vehicle, today: LocalDate, leadDays: Int, oilIntervalMonths: Int): List<Reminder> {
    if (vehicle.isSold) return emptyList()
    val threshold = today.plusDays(leadDays.toLong())
    val dueDates = listOfNotNull(
        vehicle.nextInspectionDate?.let { ReminderKind.INSPECTION to it },
        vehicle.nextGasCheckDate?.let { ReminderKind.GAS_CHECK to it },
        vehicle.nextLeakTestDate?.let { ReminderKind.LEAK_TEST to it },
        vehicle.lastOilChangeDate?.plusMonths(oilIntervalMonths.toLong())?.let { ReminderKind.OIL_CHANGE to it },
    )
    return dueDates
        .filter { (_, dueDate) -> !dueDate.isAfter(threshold) }
        .map { (kind, dueDate) -> Reminder(kind, dueDate, overdue = dueDate.isBefore(today)) }
}

/**
 * Fällige Erinnerungen aus ablaufenden Fahrzeugdokumenten, analog zu [dueReminders]: ein Eintrag je
 * Dokument mit gesetztem [VehicleDocument.expiryDate], das höchstens [leadDays] Tage in der Zukunft
 * liegt oder bereits abgelaufen ist. [Reminder.label] trägt den Dokumenttitel, [Reminder.documentId]
 * dessen id.
 */
fun documentReminders(documents: List<VehicleDocument>, today: LocalDate, leadDays: Int): List<Reminder> {
    val threshold = today.plusDays(leadDays.toLong())
    return documents.mapNotNull { document ->
        val dueDate = document.expiryDate ?: return@mapNotNull null
        if (dueDate.isAfter(threshold)) return@mapNotNull null
        Reminder(ReminderKind.DOCUMENT_EXPIRY, dueDate, overdue = dueDate.isBefore(today), label = document.title, documentId = document.id)
    }
}
