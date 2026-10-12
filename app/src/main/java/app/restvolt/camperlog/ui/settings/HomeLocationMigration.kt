package app.restvolt.camperlog.ui.settings

import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.HomeLocationRepository

/**
 * Übernimmt einmalig die alte, einzelne Zuhause-Adresse aus [settings] als ersten Eintrag der
 * Historie in [repository]. Läuft nur, solange [HomeLocationSettings.migratedToHistory] noch
 * `false` ist, und setzt es anschließend immer auf `true` - unabhängig davon, ob überhaupt eine
 * Adresse zu übernehmen war.
 */
suspend fun migrateHomeLocationToHistory(settings: HomeLocationSettings, repository: HomeLocationRepository) {
    if (settings.migratedToHistory) return
    val legacy = settings.values.value
    if (legacy.hasLocation) {
        repository.save(
            HomeLocation(
                name = legacy.name,
                latitude = legacy.latitude,
                longitude = legacy.longitude,
                coordinateSource = legacy.coordinateSource,
            ),
        )
    }
    settings.migratedToHistory = true
}
