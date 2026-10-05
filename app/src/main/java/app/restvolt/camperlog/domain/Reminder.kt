package app.restvolt.camperlog.domain

import java.time.LocalDate

/** Art der Erinnerung. */
enum class ReminderKind { INSPECTION, GAS_CHECK, OIL_CHANGE }

/** Fällige Erinnerung; [overdue], wenn [dueDate] bereits vor dem Bezugstag liegt. */
data class Reminder(val kind: ReminderKind, val dueDate: LocalDate, val overdue: Boolean)

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
        vehicle.lastOilChangeDate?.plusMonths(oilIntervalMonths.toLong())?.let { ReminderKind.OIL_CHANGE to it },
    )
    return dueDates
        .filter { (_, dueDate) -> !dueDate.isAfter(threshold) }
        .map { (kind, dueDate) -> Reminder(kind, dueDate, overdue = dueDate.isBefore(today)) }
}
