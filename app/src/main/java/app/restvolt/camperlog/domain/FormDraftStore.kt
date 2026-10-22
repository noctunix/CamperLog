package app.restvolt.camperlog.domain

/** Dauerhafte lokale Rohentwürfe, getrennt von bestätigten Fachdaten und Sicherungen. */
interface FormDraftStore {
    /** Liest den Entwurf für [kind] und [recordId], falls vorhanden. */
    suspend fun get(kind: String, recordId: Long): String?

    /** Speichert den vollständigen Rohentwurf für [kind] und [recordId]. */
    suspend fun put(kind: String, recordId: Long, payload: String)

    /** Entfernt den Entwurf für [kind] und [recordId]. */
    suspend fun delete(kind: String, recordId: Long)
}
