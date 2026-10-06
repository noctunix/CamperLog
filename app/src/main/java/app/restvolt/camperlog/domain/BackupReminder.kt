package app.restvolt.camperlog.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Baseline für "noch nie gesichert", unterscheidbar von "noch nicht gemeldet" (`null`) in [shouldNotifyBackupOverdue]. */
private val NEVER_BACKED_UP: Instant = Instant.EPOCH

/**
 * Ob die letzte Sicherung [lastBackupAt] mehr als [reminderWeeks] Wochen zurückliegt, bezogen auf
 * [now]. `reminderWeeks <= 0` bedeutet "aus" und ist nie überfällig; `lastBackupAt == null` (noch nie
 * gesichert) ist immer überfällig, sobald die Erinnerung an ist.
 */
fun isBackupOverdue(lastBackupAt: Instant?, reminderWeeks: Int, now: Instant): Boolean {
    if (reminderWeeks <= 0) return false
    val last = lastBackupAt ?: return true
    return !now.isBefore(last.plus(reminderWeeks * 7L, ChronoUnit.DAYS))
}

/**
 * Ob der tägliche Hintergrund-Check jetzt eine neue Sicherungs-Erinnerung senden soll: die Sicherung
 * muss überfällig sein ([isBackupOverdue]) und dieser Überfälligkeitszeitraum darf noch nicht
 * gemeldet worden sein. Der Zeitraum wird durch [backupNotificationBaseline] von [lastBackupAt]
 * identifiziert, gegen den gespeicherten Wert [lastNotifiedBackupBaseline] verglichen; eine neue
 * Sicherung ändert die Baseline und erlaubt dadurch eine erneute Meldung im nächsten Zeitraum.
 */
fun shouldNotifyBackupOverdue(lastBackupAt: Instant?, reminderWeeks: Int, now: Instant, lastNotifiedBackupBaseline: Instant?): Boolean =
    isBackupOverdue(lastBackupAt, reminderWeeks, now) && backupNotificationBaseline(lastBackupAt) != lastNotifiedBackupBaseline

/** Nach einer Meldung zu speichernder Bezugswert; siehe [shouldNotifyBackupOverdue]. */
fun backupNotificationBaseline(lastBackupAt: Instant?): Instant = lastBackupAt ?: NEVER_BACKED_UP

/**
 * Ob der Daten-Screen die Sicherungs-Erinnerung als Karte zeigen soll: nur solange Benachrichtigungen
 * aus sind, denn sonst übernimmt das der tägliche Hintergrund-Check.
 */
fun shouldShowBackupReminderCard(notificationsEnabled: Boolean, lastBackupAt: Instant?, reminderWeeks: Int, now: Instant): Boolean =
    !notificationsEnabled && isBackupOverdue(lastBackupAt, reminderWeeks, now)
