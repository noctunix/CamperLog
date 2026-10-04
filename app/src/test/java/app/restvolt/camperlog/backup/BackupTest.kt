package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class BackupTest {

    private val nok = Currency.getInstance("NOK")
    private val jpy = Currency.getInstance("JPY")
    private val eur = Currency.getInstance("EUR")

    private fun tour(uuid: String = "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60", destination: String = "Lofoten") = Tour(
        uuid = uuid,
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 14),
        destination = destination,
        tourType = TourType.VACATION,
        travelDays = 14,
        overnightStays = 13,
        distanceKm = 4210,
        costs = listOf(Money(123456, eur), Money(320000, nok), Money(1500, jpy)),
        pitchAssigned = true,
        electricityFlatRate = ElectricityFlatRate.NOT_USED,
        lteQuality = LteQuality.OK,
        pitchSlope = PitchSlope.SLOPED,
        levelingBlocksUsed = true,
        notes = "Zeile 1\n\"Zitat\", =SUM(A1)",
        mapLink = "https://maps.example.org/?q=1",
        createdAt = Instant.parse("2026-07-15T08:00:00Z"),
        updatedAt = Instant.parse("2026-07-16T09:30:00.123Z"),
    )

    private val backup = Backup(
        exportedAt = Instant.parse("2026-10-04T12:00:00Z"),
        mainCurrency = nok,
        rates = listOf(ExchangeRate(nok, BigDecimal("11.4850"), LocalDate.of(2026, 10, 1), "EZB")),
        tours = listOf(tour(), tour(uuid = "1b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60", destination = "Ostsee").copy(mapLink = null)),
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup

    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    /** Ersetzt in der Sicherung [backup] einen Teiltext, um gezielt ungültige Dateien zu bauen. */
    private fun encodedWith(old: String, new: String): String {
        val text = encodeBackup(backup)
        check(old in text) { "$old nicht in der Sicherung" }
        return text.replaceFirst(old, new)
    }

    @Test
    fun roundTrip_keepsAllValues() {
        val decoded = success(encodeBackup(backup))

        assertEquals(backup.exportedAt, decoded.exportedAt)
        assertEquals(backup.mainCurrency, decoded.mainCurrency)
        assertEquals(1, decoded.rates.size)
        assertEquals(0, BigDecimal("11.485").compareTo(decoded.rates.single().perEuro))
        assertEquals(backup.rates.single().copy(perEuro = decoded.rates.single().perEuro), decoded.rates.single())
        assertEquals(backup.tours, decoded.tours)
    }

    @Test
    fun encode_writesAmountsAsDecimalTextPerCurrencyPrecision() {
        val text = encodeBackup(backup)

        assert("\"amount\": \"1234.56\"" in text)
        assert("\"amount\": \"3200.00\"" in text)
        assert("\"amount\": \"1500\"" in text)
        assert("\"schemaVersion\": 1" in text)
        assert("\"format\": \"camperlog-backup\"" in text)
    }

    @Test
    fun read_acceptsBomAndIgnoresUnknownFields() {
        val text = "\uFEFF" + encodedWith("\"mainCurrency\"", "\"futureField\": {\"a\": 1}, \"mainCurrency\"")

        val result = readBackup(ByteArrayInputStream(text.toByteArray()))

        assertEquals(backup.tours, (result as BackupReadResult.Success).backup.tours)
    }

    @Test
    fun read_rejectsTooLargeInput() {
        val bytes = ByteArray(MAX_BACKUP_BYTES + 1) { ' '.code.toByte() }

        assertEquals(BackupReadResult.Failure(BackupError.TOO_LARGE), readBackup(ByteArrayInputStream(bytes)))
    }

    @Test
    fun read_rejectsInvalidUtf8() {
        val bytes = byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D)

        assertEquals(BackupError.NOT_A_BACKUP, (readBackup(ByteArrayInputStream(bytes)) as BackupReadResult.Failure).error)
    }

    @Test
    fun decode_rejectsNonBackups() {
        listOf("", "kein json", "[]", "{}", "{\"format\": \"other\", \"schemaVersion\": 1}", "\"camperlog-backup\"")
            .forEach { assertEquals(it, BackupError.NOT_A_BACKUP, failure(it)?.error) }
    }

    @Test
    fun decode_rejectsNewerVersion() {
        assertEquals(BackupError.NEWER_VERSION, failure(encodedWith("\"schemaVersion\": 1", "\"schemaVersion\": 2"))?.error)
    }

    @Test
    fun decode_rejectsMissingOrInvalidVersion() {
        listOf("\"schemaVersion\": 0", "\"schemaVersion\": \"1\"", "\"schemaVersion\": 1.5", "\"v\": 1").forEach {
            assertEquals(it, BackupError.INVALID_DATA, failure(encodedWith("\"schemaVersion\": 1", it))?.error)
        }
    }

    @Test
    fun decode_rejectsInvalidTopLevelValues() {
        listOf(
            "\"mainCurrency\": \"NOK\"" to "\"mainCurrency\": \"XXX1\"",
            "\"exportedAt\": \"2026-10-04T12:00:00Z\"" to "\"exportedAt\": \"gestern\"",
            "\"currency\": \"NOK\",\n            \"perEuro\": \"11.485\"" to "\"currency\": \"EUR\",\n            \"perEuro\": \"11.485\"",
            "\"perEuro\": \"11.485\"" to "\"perEuro\": \"0\"",
            "\"perEuro\": \"11.485\"" to "\"perEuro\": \"1.1234567\"",
            "\"perEuro\": \"11.485\"" to "\"perEuro\": \"-1\"",
            "\"perEuro\": \"11.485\"" to "\"perEuro\": \"1e3\"",
        ).forEach { (old, new) ->
            val result = failure(encodedWith(old, new))
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA), result)
        }
    }

    @Test
    fun decode_rejectsDuplicateRates() {
        val twice = backup.copy(rates = backup.rates + backup.rates)

        assertEquals(BackupError.INVALID_DATA, failure(encodeBackup(twice))?.error)
    }

    @Test
    fun decode_reportsNumberOfInvalidTour() {
        listOf(
            "\"uuid\": \"1b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60\"" to "\"uuid\": \"keine-uuid\"",
            "\"destination\": \"Ostsee\"" to "\"destination\": \"   \"",
            "\"destination\": \"Ostsee\"" to "\"destination\": \"${"x".repeat(MAX_DESTINATION_LENGTH + 1)}\"",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = 2), failure(encodedWith(old, new)))
        }
    }

    @Test
    fun decode_rejectsInvalidTourValues() {
        listOf(
            "\"endDate\": \"2026-07-14\"" to "\"endDate\": \"2026-06-30\"",
            "\"startDate\": \"2026-07-01\"" to "\"startDate\": \"2026-02-30\"",
            "\"tourType\": \"VACATION\"" to "\"tourType\": \"Urlaub\"",
            "\"lteQuality\": \"OK\"" to "\"lteQuality\": \"ok\"",
            "\"travelDays\": 14" to "\"travelDays\": -1",
            "\"travelDays\": 14" to "\"travelDays\": 99999999999",
            "\"overnightStays\": 13" to "\"overnightStays\": 15",
            "\"distanceKm\": 4210" to "\"distanceKm\": 42.1",
            "\"pitchAssigned\": true" to "\"pitchAssigned\": \"ja\"",
            "\"mapLink\": \"https://maps.example.org/?q=1\"" to "\"mapLink\": \"javascript:alert(1)\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"1234.567\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"-1\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"1,5\"",
            "\"amount\": \"1500\"" to "\"amount\": \"1500.5\"",
            "\"currency\": \"JPY\"" to "\"currency\": \"NOK\"",
            "\"createdAt\": \"2026-07-15T08:00:00Z\"" to "\"createdAt\": \"2026-07-15\"",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = 1), failure(encodedWith(old, new)))
        }
    }

    @Test
    fun decode_rejectsMissingRequiredField() {
        val result = failure(encodedWith("\"tourType\": \"VACATION\",", ""))

        assertEquals(BackupError.INVALID_DATA, result?.error)
    }

    @Test
    fun decode_rejectsDuplicateUuids() {
        val twice = backup.copy(tours = listOf(tour(), tour(destination = "Nochmal")))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = 2), failure(encodeBackup(twice)))
    }

    @Test
    fun decode_normalizesUuidAndDropsEmptyLink() {
        val text = encodedWith("\"uuid\": \"0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60\"", "\"uuid\": \"0B6F5E2A-6C1D-4E8A-9F3B-2D7C1A4E5F60\"")
            .replaceFirst("\"mapLink\": \"https://maps.example.org/?q=1\"", "\"mapLink\": \"  \"")

        val first = success(text).tours.first()

        assertEquals("0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60", first.uuid)
        assertEquals(null, first.mapLink)
    }

    @Test
    fun decode_acceptsTrailingZerosWithinPrecision() {
        val first = success(encodedWith("\"amount\": \"1500\"", "\"amount\": \"1500.00\"")).tours.first()

        assertEquals(Money(1500, jpy), first.costs.last())
    }
}
