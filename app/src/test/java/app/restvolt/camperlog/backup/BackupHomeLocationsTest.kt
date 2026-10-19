package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.HomeLocation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * Heimatadressen im JSON-Teil einer Sicherung: Rundgang und Validierung (siehe `BackupTest` für den
 * Rest des Formats, `BackupChecklistsTest` für das analoge Muster einer flachen Liste ohne Fremdschlüssel).
 */
class BackupHomeLocationsTest {

    private val eur = Currency.getInstance("EUR")
    private val uuid = "9b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"

    private fun location() = HomeLocation(
        uuid = uuid,
        name = "Zuhause",
        latitude = 48.1,
        longitude = 11.6,
        coordinateSource = CoordinateSource.ENTERED,
        validFrom = LocalDate.of(2026, 1, 1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.parse("2026-07-04T20:00:00Z"),
    )

    private fun backup(homeLocations: List<HomeLocation> = listOf(location())) = Backup(
        exportedAt = Instant.parse("2026-10-06T12:00:00Z"),
        mainCurrency = eur,
        rates = emptyList(),
        tours = emptyList(),
        homeLocations = homeLocations,
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup
    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    @Test
    fun roundTrip_keepsHomeLocationWithCoordinatesAndValidity() {
        val decoded = success(encodeBackup(backup()))

        val decodedLocation = decoded.homeLocations.single()
        assertEquals("Zuhause", decodedLocation.name)
        assertEquals(48.1, decodedLocation.latitude!!, 0.0)
        assertEquals(11.6, decodedLocation.longitude!!, 0.0)
        assertEquals(CoordinateSource.ENTERED, decodedLocation.coordinateSource)
        assertEquals(LocalDate.of(2026, 1, 1), decodedLocation.validFrom)
        assertEquals(null, decodedLocation.validTo)
        assertEquals(Instant.parse("2026-07-04T20:00:00Z"), decodedLocation.updatedAt)
    }

    @Test
    fun roundTrip_keepsHomeLocationWithoutCoordinates() {
        val withoutCoordinates = location().copy(latitude = null, longitude = null, coordinateSource = null)
        val decoded = success(encodeBackup(backup(homeLocations = listOf(withoutCoordinates))))

        val decodedLocation = decoded.homeLocations.single()
        assertEquals(null, decodedLocation.latitude)
        assertEquals(false, decodedLocation.hasLocation)
    }

    @Test
    fun missingHomeLocations_decodesAsEmpty_forOlderBackups() {
        // Ein Backup vor Formatversion 22 kennt dieses Feld gar nicht.
        val withoutField = Json.parseToJsonElement(encodeBackup(backup())).jsonObject.filterKeys { it != "homeLocations" }
        val text = JsonObject(withoutField).toString()

        val decoded = success(text)

        assertEquals(emptyList<HomeLocation>(), decoded.homeLocations)
    }

    @Test
    fun homeLocationWithOnlyLatitude_isRejected() {
        val broken = backup(homeLocations = listOf(location().copy(longitude = null)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, homeLocationNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun homeLocationWithValidToBeforeValidFrom_isRejected() {
        val broken = backup(
            homeLocations = listOf(location().copy(validFrom = LocalDate.of(2026, 6, 1), validTo = LocalDate.of(2026, 1, 1))),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, homeLocationNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateHomeLocationUuid_isRejected() {
        val broken = backup(homeLocations = listOf(location(), location()))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, homeLocationNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun homeLocationNameTooLong_isRejected() {
        val broken = backup(homeLocations = listOf(location().copy(name = "x".repeat(MAX_DESTINATION_LENGTH + 1))))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, homeLocationNumber = 1), failure(encodeBackup(broken)))
    }
}
