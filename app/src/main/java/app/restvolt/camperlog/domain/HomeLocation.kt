package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Eine Heimatadresse mit Gültigkeitszeitraum, für die automatische "Beginnt zu Hause"-Station
 * neuer Touren. [validFrom]/[validTo] `null` bedeutet "schon immer" bzw. "bis auf Weiteres"; bei
 * mehreren Einträgen dürfen sich ihre Zeiträume nicht überlappen (wird von der Oberfläche
 * durchgesetzt, nicht hier).
 */
data class HomeLocation(
    val id: Long = 0,
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateSource: CoordinateSource? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /** `true`, sobald eine Koordinate gesetzt wurde. */
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** Zugriff auf die Heimatadressen-Historie. */
interface HomeLocationRepository {
    /** Alle Adressen, laufende zuerst, sonst neueste Gültigkeit zuerst. */
    fun observeAll(): Flow<List<HomeLocation>>

    /** Die am [date] gültige Adresse, oder `null`, wenn keine passt. */
    suspend fun locationAt(date: LocalDate): HomeLocation?

    /** Legt [location] an (id `0`) oder aktualisiert sie; liefert die (neue oder bestehende) id. */
    suspend fun save(location: HomeLocation): Long

    suspend fun delete(id: Long)
}
