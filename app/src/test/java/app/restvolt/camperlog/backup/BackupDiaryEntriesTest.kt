package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.DiaryEntry
import app.restvolt.camperlog.domain.MAX_ODOMETER_KM
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency

/** Tagebucheinträge im JSON-Teil einer Sicherung: Rundgang und Validierung (siehe `BackupTest` für den Rest des Formats). */
class BackupDiaryEntriesTest {

    private val nok = Currency.getInstance("NOK")
    private val vehicleUuid = "2b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val tourUuid = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val entryUuid = "9b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"

    private fun vehicle() = Vehicle(uuid = vehicleUuid, name = "Bluebird", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun tour() = Tour(
        uuid = tourUuid,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 10),
        destination = "Lofoten",
        tourType = TourType.VACATION,
        travelDays = 10,
        overnightStays = 9,
        distanceKm = 2000,
        costs = emptyList(),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun entry(date: LocalDate = LocalDate.of(2026, 7, 4)) = DiaryEntry(
        uuid = entryUuid,
        tourId = 0,
        date = date,
        text = "Langer Tag am Fjord.",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.parse("2026-07-04T20:00:00Z"),
    )

    private fun backup(diaryEntries: List<BackupDiaryEntry> = listOf(BackupDiaryEntry(entry(), tourUuid))) = Backup(
        exportedAt = Instant.parse("2026-10-06T12:00:00Z"),
        mainCurrency = nok,
        rates = emptyList(),
        tours = listOf(tour()),
        tourVehicleUuid = mapOf(tourUuid to vehicleUuid),
        vehicles = listOf(BackupVehicle(vehicle(), emptyList(), emptyList())),
        diaryEntries = diaryEntries,
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup
    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure
    private fun encodedWith(old: String, new: String) = encodeBackup(backup()).replace(old, new)

    @Test
    fun roundTrip_keepsDiaryEntryWithTourUuid() {
        val decoded = success(encodeBackup(backup()))

        val diaryEntry = decoded.diaryEntries.single()
        assertEquals("Langer Tag am Fjord.", diaryEntry.entry.text)
        assertEquals(LocalDate.of(2026, 7, 4), diaryEntry.entry.date)
        assertEquals(tourUuid, diaryEntry.tourUuid)
        assertEquals(Instant.parse("2026-07-04T20:00:00Z"), diaryEntry.entry.updatedAt)
    }

    @Test
    fun missingDiaryEntries_decodeAsEmpty_forOlderBackups() {
        // Ein Backup vor Formatversion 8 kennt das Feld gar nicht.
        val withoutField = Json.parseToJsonElement(encodeBackup(backup(emptyList()))).jsonObject.filterKeys { it != "diaryEntries" }
        val text = JsonObject(withoutField).toString()

        val decoded = success(text)

        assertEquals(emptyList<BackupDiaryEntry>(), decoded.diaryEntries)
    }

    @Test
    fun diaryEntryWithUnknownTourUuid_isRejected() {
        val broken = backup(listOf(BackupDiaryEntry(entry(), "ffffffff-ffff-4fff-8fff-ffffffffffff")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun duplicateDiaryEntryUuid_isRejected() {
        val broken = backup(
            listOf(
                BackupDiaryEntry(entry(date = LocalDate.of(2026, 7, 4)), tourUuid),
                BackupDiaryEntry(entry(date = LocalDate.of(2026, 7, 5)), tourUuid),
            ),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun twoEntriesForTheSameTourAndDate_isRejected() {
        val broken = backup(
            listOf(
                BackupDiaryEntry(entry(date = LocalDate.of(2026, 7, 4)), tourUuid),
                BackupDiaryEntry(
                    entry(date = LocalDate.of(2026, 7, 4)).copy(uuid = "8b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"),
                    tourUuid,
                ),
            ),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 2), failure(encodeBackup(broken)))
    }

    @Test
    fun blankDiaryEntryText_isRejected() {
        val broken = backup(listOf(BackupDiaryEntry(entry().copy(text = "   "), tourUuid)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun tooLongDiaryEntryText_isRejected() {
        val broken = backup(listOf(BackupDiaryEntry(entry().copy(text = "x".repeat(MAX_DIARY_TEXT_LENGTH + 1)), tourUuid)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 1), failure(encodeBackup(broken)))
    }

    @Test
    fun roundTrip_keepsTimeOdometerAndWeather() {
        val withFields = backup(
            listOf(BackupDiaryEntry(entry().copy(time = LocalTime.of(18, 30), odometerKm = 48_300, weather = "Sonnig, 22 °C"), tourUuid)),
        )

        val decoded = success(encodeBackup(withFields))

        val diaryEntry = decoded.diaryEntries.single().entry
        assertEquals(LocalTime.of(18, 30), diaryEntry.time)
        assertEquals(48_300, diaryEntry.odometerKm)
        assertEquals("Sonnig, 22 °C", diaryEntry.weather)
    }

    @Test
    fun read_importsOlderBackupsMissingTimeOdometerAndWeather() {
        val text = applyAll(
            encodeBackup(backup(listOf(BackupDiaryEntry(entry().copy(time = LocalTime.of(18, 30), odometerKm = 48_300, weather = "Sonnig"), tourUuid)))),
            listOf(
                "\"time\": \"18:30\"," to "",
                "\"odometerKm\": 48300," to "",
                "\"weather\": \"Sonnig\"," to "",
            ),
        )

        val decoded = success(text)

        val diaryEntry = decoded.diaryEntries.single().entry
        assertEquals(null, diaryEntry.time)
        assertEquals(null, diaryEntry.odometerKm)
        assertEquals("", diaryEntry.weather)
    }

    @Test
    fun diaryEntryWithInvalidTime_isRejected() {
        val text = encodedWith("\"time\": null,", "\"time\": \"kaumzeit\",")

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 1), failure(text))
    }

    @Test
    fun diaryEntryWithOdometerOutOfBounds_isRejected() {
        listOf(
            "\"odometerKm\": null," to "\"odometerKm\": -1,",
            "\"odometerKm\": null," to "\"odometerKm\": ${MAX_ODOMETER_KM + 1},",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 1), failure(encodedWith(old, new)))
        }
    }

    @Test
    fun tooLongDiaryEntryWeather_isRejected() {
        val broken = backup(listOf(BackupDiaryEntry(entry().copy(weather = "x".repeat(MAX_DESTINATION_LENGTH + 1)), tourUuid)))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, diaryEntryNumber = 1), failure(encodeBackup(broken)))
    }

    private fun applyAll(text: String, replacements: List<Pair<String, String>>): String =
        replacements.fold(text) { acc, (old, new) ->
            check(old in acc) { "\"$old\" nicht gefunden" }
            acc.replace(old, new)
        }
}
