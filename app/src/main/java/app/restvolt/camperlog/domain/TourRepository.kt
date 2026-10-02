package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow

/** Zugriff auf alle gespeicherten Touren. */
interface TourRepository {

    /** Liefert alle Touren, neueste zuerst, und aktualisiert sich bei Änderungen. */
    fun observeTours(): Flow<List<Tour>>

    /** Liefert die Tour mit [id] oder `null`, falls sie nicht (mehr) existiert. */
    fun observeTour(id: Long): Flow<Tour?>

    /** Liefert alle Touren chronologisch aufsteigend für den Export. */
    suspend fun allTours(): List<Tour>

    /**
     * Legt [tour] an, wenn ihre id 0 ist, sonst wird sie aktualisiert.
     * Zeitstempel werden dabei vom Repository gesetzt.
     *
     * @return die id der gespeicherten Tour
     */
    suspend fun save(tour: Tour): Long

    /** Löscht die Tour mit [id]. */
    suspend fun delete(id: Long)

    /** Legt eine zuvor gelöschte [tour] mit ihrer bisherigen id und ihren Zeitstempeln wieder an. */
    suspend fun restore(tour: Tour)

    /** Liefert die Gesamtwerte über alle Touren. */
    fun observeTotals(): Flow<TourTotals>

    /** Liefert die Werte pro Jahr, absteigend nach Jahr sortiert. */
    fun observeYearTotals(): Flow<List<YearTotals>>
}
