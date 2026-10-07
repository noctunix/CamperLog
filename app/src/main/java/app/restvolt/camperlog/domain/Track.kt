package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ein aufgezeichneter Trackpunkt einer Tour. [segment] zählt die Aufzeichnungen je Tour hoch:
 * jedes Starten beginnt ein neues Segment, damit Pausen in der Karte nicht als Linie erscheinen.
 * [accuracyM] und [altitudeM] fehlen, wenn das Gerät dazu nichts liefert.
 */
data class TrackPoint(
    val id: Long = 0,
    val tourId: Long,
    val segment: Int,
    val recordedAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Int? = null,
    val altitudeM: Int? = null,
)

/** Kurzüberblick über den Track einer Tour: Anzahl Punkte und Segmente. */
data class TrackSummary(val points: Int, val segments: Int) {
    companion object {
        val EMPTY = TrackSummary(0, 0)
    }
}

/**
 * Wählbare Abstände zwischen zwei Trackpunkten (Owner-Entscheidung: Liste statt Schieberegler,
 * TalkBack-freundlich). [storageValue] ist der stabile Wert in den Einstellungen.
 */
enum class TrackInterval(val seconds: Int) {
    SECONDS_30(30),
    MINUTES_2(120),
    MINUTES_5(300),
    MINUTES_15(900),
    MINUTES_30(1800),
    MINUTES_60(3600),
    MINUTES_120(7200),
    ;

    val storageValue: String get() = name

    companion object {
        /** Standard für neue Installationen. */
        val DEFAULT = MINUTES_15

        /** Abstand, solange das Gerät lädt und "Häufiger beim Laden" an ist. */
        val WHILE_CHARGING = MINUTES_2

        /** Liest den gespeicherten Wert; unbekannte oder fehlende Werte ergeben [DEFAULT]. */
        fun parse(raw: String?): TrackInterval = entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/**
 * Parameter für `LocationRequestCompat`, aus dem Abstand abgeleitet wie in Voltocol:
 * Fix-Intervall, Mindestdistanz (nur Liefer-Filter, kein Stromsparhebel) und Batching ab dem
 * doppelten Intervall. Bei langen Abständen bringt Batching nichts, dann ist [maxUpdateDelayMillis] 0.
 */
data class TrackSamplingConfig(val intervalMillis: Long, val minDistanceMeters: Float, val maxUpdateDelayMillis: Long) {
    companion object {
        /** Längster Abstand, bei dem noch gebündelt geliefert wird. */
        private const val MAX_BATCHED_INTERVAL_SECONDS = 300

        fun forInterval(interval: TrackInterval): TrackSamplingConfig {
            val millis = interval.seconds * 1000L
            val minDistance = when {
                interval.seconds <= 30 -> 20f
                interval.seconds <= 120 -> 50f
                else -> 100f
            }
            val maxDelay = if (interval.seconds <= MAX_BATCHED_INTERVAL_SECONDS) 2 * millis else 0L
            return TrackSamplingConfig(millis, minDistance, maxDelay)
        }
    }
}

/**
 * Der tatsächlich genutzte Abstand: beim Laden mit [fasterWhileCharging] höchstens
 * [TrackInterval.WHILE_CHARGING], sonst [selected].
 */
fun effectiveTrackInterval(selected: TrackInterval, fasterWhileCharging: Boolean, charging: Boolean): TrackInterval =
    if (fasterWhileCharging && charging && selected.seconds > TrackInterval.WHILE_CHARGING.seconds) {
        TrackInterval.WHILE_CHARGING
    } else {
        selected
    }

/** Fixes ungenauer als dies werden verworfen; sie zeichnen Zickzack statt Strecke. */
const val TRACK_MAX_ACCURACY_M: Int = 100

/** Ob ein Fix mit [accuracyM] in den Track darf; ohne Angabe wird er behalten. */
fun isUsableTrackFix(accuracyM: Int?): Boolean = accuracyM == null || accuracyM <= TRACK_MAX_ACCURACY_M

/** Großkreisabstand in Metern zwischen zwei Koordinaten (Haversine, mittlerer Erdradius). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private const val EARTH_RADIUS_M = 6_371_008.8

/**
 * Länge des Tracks in Metern: Summe der Abstände aufeinanderfolgender Punkte je Segment, sortiert nach
 * Zeit. Zwischen Segmenten wird nicht verbunden, eine Aufzeichnungspause zählt also nicht mit.
 */
fun trackLengthMeters(points: List<TrackPoint>): Double =
    points.groupBy { it.segment }.values.sumOf { segment ->
        segment.sortedBy { it.recordedAt }.zipWithNext { a, b ->
            distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }.sum()
    }

/** Zugriff auf die gespeicherten Trackpunkte. */
interface TrackRepository {

    /** Liefert die Punkte der Tour [tourId], aufsteigend nach Segment und Zeit. */
    fun observeForTour(tourId: Long): Flow<List<TrackPoint>>

    /** Liefert Anzahl Punkte und Segmente der Tour [tourId]. */
    fun observeSummary(tourId: Long): Flow<TrackSummary>

    /** Liefert alle Punkte für den Sicherungs-Export, ohne festgelegte Reihenfolge. */
    suspend fun allPoints(): List<TrackPoint>

    /** Die Segmentnummer für eine neue Aufzeichnung der Tour [tourId]: höchstes bisheriges Segment + 1. */
    suspend fun nextSegment(tourId: Long): Int

    /** Speichert [points]; Punkte mit gleicher Tour und gleichem Zeitpunkt werden übersprungen. */
    suspend fun addAll(points: List<TrackPoint>)

    /** Löscht den gesamten Track der Tour [tourId]. */
    suspend fun deleteForTour(tourId: Long)
}
