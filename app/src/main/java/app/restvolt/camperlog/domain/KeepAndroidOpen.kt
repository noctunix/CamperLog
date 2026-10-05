package app.restvolt.camperlog.domain

import java.time.Duration
import java.time.Instant

/** Wie lange ein frischer Start ohne Daten vor dem Hinweis verschont bleibt. */
private val SETTLE_IN_GRACE_PERIOD: Duration = Duration.ofDays(3)

/** Abstand zwischen zwei Anzeigen des Hinweises, solange die Petition nicht unterstützt wurde. */
private val REMINDER_INTERVAL: Duration = Duration.ofDays(14)

/** Gespeicherter Zustand des „Keep Android Open“-Hinweises; siehe [shouldShowKeepAndroidOpen]. */
data class KeepAndroidOpenState(
    val firstLaunchAt: Instant?,
    val lastShownAt: Instant?,
    val supported: Boolean,
)

/**
 * Ob der „Keep Android Open“-Hinweis zum Zeitpunkt [now] angezeigt werden soll.
 *
 * Er erscheint nie als erster Eindruck: Erst wenn [hasData] zutrifft (eine Tour oder ein
 * Bordbuch-Eintrag wurde gespeichert) oder seit [KeepAndroidOpenState.firstLaunchAt] mindestens
 * [SETTLE_IN_GRACE_PERIOD] vergangen ist, gilt die App als „eingelebt“. Danach wird er gezeigt,
 * wenn er noch nie oder zuletzt vor mindestens [REMINDER_INTERVAL] gezeigt wurde. Nach
 * [KeepAndroidOpenState.supported] erscheint er nie wieder.
 */
fun shouldShowKeepAndroidOpen(state: KeepAndroidOpenState, hasData: Boolean, now: Instant): Boolean {
    if (state.supported) return false

    val settledIn = hasData ||
        state.lastShownAt != null ||
        (state.firstLaunchAt != null && !Duration.between(state.firstLaunchAt, now).minus(SETTLE_IN_GRACE_PERIOD).isNegative)
    if (!settledIn) return false

    val lastShown = state.lastShownAt ?: return true
    return !Duration.between(lastShown, now).minus(REMINDER_INTERVAL).isNegative
}
