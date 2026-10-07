package app.restvolt.camperlog.backup

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LogEntry
import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.MAX_BATTERY_AH
import app.restvolt.camperlog.domain.MAX_DIMENSION_M
import app.restvolt.camperlog.domain.MAX_ODOMETER_KM
import app.restvolt.camperlog.domain.MAX_PHONE_LENGTH
import app.restvolt.camperlog.domain.MAX_POWER_KW
import app.restvolt.camperlog.domain.MAX_SOLAR_WP
import app.restvolt.camperlog.domain.MAX_TANK_L
import app.restvolt.camperlog.domain.MAX_TIRE_PRESSURE_BAR
import app.restvolt.camperlog.domain.MAX_WEIGHT_KG
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.SiteKind
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCost
import app.restvolt.camperlog.domain.StationService
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.Vehicle
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

    private val vehicleUuid = "2b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val repairUuid = "3b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val logEntryUuid = "4b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"
    private val stationUuid = "5b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60"

    private fun station(uuid: String = stationUuid) = Station(
        uuid = uuid,
        vehicleId = 0,
        tourId = null,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 4),
        name = "Camping Moskenes",
        place = "Moskenes, Norwegen",
        latitude = 68.0912,
        longitude = 13.1023,
        coordinateSource = CoordinateSource.ENTERED,
        notes = "Schöner Blick",
        nights = 2,
        siteKind = SiteKind.CAMPSITE,
        pitchAssigned = true,
        lteQuality = LteQuality.GOOD,
        pitchSlope = PitchSlope.LEVEL,
        levelingBlocksUsed = false,
        electricityBilling = ElectricityBilling.BASE_PLUS_METERED,
        electricityCurrency = eur,
        electricityBaseFee = Money(500, eur),
        electricityPricePerKwh = BigDecimal("0.35"),
        electricityMeterStart = BigDecimal("120.5"),
        electricityMeterEnd = BigDecimal("135.75"),
        costs = listOf(StationCost(CostCategory.PITCH, Money(1500, eur), "Zwei Nächte")),
        services = setOf(StationService.CASSETTE, StationService.FRESH_WATER),
        favorite = true,
        createdAt = Instant.parse("2026-07-04T18:00:00Z"),
        updatedAt = Instant.parse("2026-07-05T08:00:00Z"),
    )

    private fun fullVehicle(uuid: String = vehicleUuid) = Vehicle(
        uuid = uuid,
        name = "Bulli",
        licensePlate = "KS-AB 123",
        manufacturer = "Volkswagen",
        model = "California",
        vin = "WV1ZZZ7HZ8H123456",
        firstRegistration = LocalDate.of(2018, 4, 1),
        notes = "Zweitfahrzeug",
        purchaseDate = LocalDate.of(2018, 5, 1),
        purchasePrice = Money(4500000, eur),
        purchaseOdometerKm = 500,
        saleDate = LocalDate.of(2026, 1, 1),
        salePrice = Money(3200000, eur),
        insurer = "HUK24",
        insurancePolicyNumber = "POL-123",
        insurancePremiumPerYear = Money(80000, eur),
        vehicleTaxPerYear = Money(30000, eur),
        lengthCm = 500,
        widthCm = 250,
        heightCm = 295,
        grossWeightKg = 3500,
        measuredEmptyWeightKg = 3020,
        breakdownProvider = "ADAC",
        breakdownMembershipNumber = "123 456 789",
        breakdownPhone = "+49 89 22 22 22",
        travelProtectionProvider = "Beispiel AG",
        travelProtectionContractNumber = "987-654",
        travelProtectionPhone = "+49 30 123456",
        insurerClaimsPhone = "+49 69 5678",
        powerKw = 130,
        tireSize = "225/75 R16 C",
        tirePressureFrontMbar = 2500,
        tirePressureRearMbar = 2800,
        fuelTankDl = 900,
        adBlueTankDl = 150,
        freshWaterTankDl = 1000,
        greyWaterTankDl = 900,
        boilerDl = 100,
        cassetteDl = 200,
        batteryCapacityAh = 200,
        solarPowerWp = 400,
        nextInspectionDate = LocalDate.of(2027, 4, 1),
        nextGasCheckDate = LocalDate.of(2027, 5, 1),
        nextLeakTestDate = LocalDate.of(2027, 6, 1),
        lastOilChangeDate = LocalDate.of(2026, 1, 1),
        lastOilChangeOdometerKm = 12000,
        createdAt = Instant.parse("2018-05-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T09:30:00Z"),
    )

    private fun repair(uuid: String = repairUuid) = Repair(
        uuid = uuid,
        vehicleId = 0,
        date = LocalDate.of(2025, 6, 1),
        description = "Bremsen erneuert",
        odometerKm = 60000,
        cost = Money(45000, eur),
        createdAt = Instant.parse("2025-06-02T08:00:00Z"),
        updatedAt = Instant.parse("2025-06-02T08:00:00Z"),
    )

    private fun logEntry(uuid: String = logEntryUuid) = LogEntry(
        uuid = uuid,
        vehicleId = 0,
        type = LogType.CASSETTE_EMPTIED,
        date = LocalDate.of(2026, 9, 1),
        createdAt = Instant.parse("2026-09-01T08:00:00Z"),
    )

    private val vehicleBackup = backup.copy(
        tours = listOf(tour()),
        tourVehicleUuid = mapOf(tour().uuid to vehicleUuid),
        vehicles = listOf(BackupVehicle(fullVehicle(), listOf(repair()), listOf(logEntry()))),
        currentVehicleUuid = vehicleUuid,
    )

    private fun success(text: String) = (decodeBackup(text) as BackupReadResult.Success).backup

    private fun failure(text: String) = decodeBackup(text) as? BackupReadResult.Failure

    /** Ersetzt in der Sicherung [backup] einen Teiltext, um gezielt ungültige Dateien zu bauen. */
    private fun encodedWith(old: String, new: String): String {
        val text = encodeBackup(backup)
        check(old in text) { "$old nicht in der Sicherung" }
        return text.replaceFirst(old, new)
    }

    private fun vehicleEncodedWith(old: String, new: String): String {
        val text = encodeBackup(vehicleBackup)
        check(old in text) { "$old nicht in der Sicherung" }
        return text.replaceFirst(old, new)
    }

    /** Wendet mehrere Ersetzungen nacheinander auf [text] an, jede auf die erste verbliebene Fundstelle. */
    private fun applyAll(text: String, replacements: List<Pair<String, String>>): String =
        replacements.fold(text) { current, (old, new) ->
            check(old in current) { "$old nicht in der Sicherung" }
            current.replaceFirst(old, new)
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
        assertEquals(emptyMap<String, String>(), decoded.tourVehicleUuid)
        assertEquals(emptyList<BackupVehicle>(), decoded.vehicles)
        assertEquals(null, decoded.currentVehicleUuid)
        assertEquals(emptyList<Station>(), decoded.stations)
    }

    @Test
    fun roundTrip_keepsManualCountryAdjustments() {
        val withCountries = Backup(
            exportedAt = backup.exportedAt,
            mainCurrency = backup.mainCurrency,
            rates = backup.rates,
            tours = listOf(tour().copy(manualCountriesAdded = setOf("CH", "AT"), manualCountriesRemoved = setOf("DE"))),
        )

        val decoded = success(encodeBackup(withCountries))

        assertEquals(setOf("CH", "AT"), decoded.tours.single().manualCountriesAdded)
        assertEquals(setOf("DE"), decoded.tours.single().manualCountriesRemoved)
    }

    @Test
    fun read_importsOlderBackupsMissingTheManualCountryFields() {
        val text = encodedWith("\"manualCountriesAdded\": [],", "")

        val decoded = success(text)

        assertEquals(emptySet<String>(), decoded.tours.first().manualCountriesAdded)
    }

    @Test
    fun decode_rejectsTourWithUnknownManualCountryCode() {
        val text = encodedWith("\"manualCountriesAdded\": []", "\"manualCountriesAdded\": [\"ZZ\"]")

        assertEquals(BackupError.INVALID_DATA, failure(text)?.error)
    }

    @Test
    fun decode_rejectsTourWithTheSameCountryAddedAndRemoved() {
        val text = applyAll(
            encodeBackup(backup),
            listOf(
                "\"manualCountriesAdded\": []" to "\"manualCountriesAdded\": [\"CH\"]",
                "\"manualCountriesRemoved\": []" to "\"manualCountriesRemoved\": [\"CH\"]",
            ),
        )

        assertEquals(BackupError.INVALID_DATA, failure(text)?.error)
    }

    @Test
    fun roundTrip_keepsFullVehicleWithRepairsLogEntriesAndCurrentVehicle() {
        val decoded = success(encodeBackup(vehicleBackup))

        assertEquals(1, decoded.vehicles.size)
        val vehicle = decoded.vehicles.single()
        assertEquals(fullVehicle(), vehicle.vehicle)
        assertEquals(listOf(repair()), vehicle.repairs)
        assertEquals(listOf(logEntry()), vehicle.logEntries)
        assertEquals(vehicleUuid, decoded.currentVehicleUuid)
        assertEquals(mapOf(tour().uuid to vehicleUuid), decoded.tourVehicleUuid)
    }

    @Test
    fun read_importsOlderBackupsMissingTheLeakTestField() {
        val text = vehicleEncodedWith("\"nextLeakTestDate\": \"2027-06-01\",", "")

        val decoded = success(text)

        assertEquals(null, decoded.vehicles.single().vehicle.nextLeakTestDate)
    }

    @Test
    fun encode_writesAmountsAsDecimalTextPerCurrencyPrecision() {
        val text = encodeBackup(backup)

        assert("\"amount\": \"1234.56\"" in text)
        assert("\"amount\": \"3200.00\"" in text)
        assert("\"amount\": \"1500\"" in text)
        assert("\"schemaVersion\": 10" in text)
        assert("\"format\": \"camperlog-backup\"" in text)
    }

    @Test
    fun read_acceptsBomAndIgnoresUnknownFields() {
        val text = "﻿" + encodedWith("\"mainCurrency\"", "\"futureField\": {\"a\": 1}, \"mainCurrency\"")

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
        assertEquals(BackupError.NEWER_VERSION, failure(encodedWith("\"schemaVersion\": 10", "\"schemaVersion\": 11"))?.error)
    }

    @Test
    fun decode_rejectsMissingOrInvalidVersion() {
        listOf("\"schemaVersion\": 0", "\"schemaVersion\": \"1\"", "\"schemaVersion\": 1.5", "\"v\": 1").forEach {
            assertEquals(it, BackupError.INVALID_DATA, failure(encodedWith("\"schemaVersion\": 10", it))?.error)
        }
    }

    @Test
    fun decode_acceptsVersion1Backup() {
        val text = """
            {
              "format": "camperlog-backup",
              "schemaVersion": 1,
              "exportedAt": "2026-10-04T12:00:00Z",
              "mainCurrency": "NOK",
              "exchangeRates": [],
              "tours": [
                {
                  "uuid": "0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60",
                  "startDate": "2026-07-01",
                  "endDate": "2026-07-14",
                  "destination": "Lofoten",
                  "tourType": "VACATION",
                  "travelDays": 14,
                  "overnightStays": 13,
                  "distanceKm": 4210,
                  "costs": [],
                  "pitchAssigned": true,
                  "electricityFlatRate": "NOT_USED",
                  "lteQuality": "OK",
                  "pitchSlope": "SLOPED",
                  "levelingBlocksUsed": true,
                  "notes": "",
                  "mapLink": null,
                  "createdAt": "2026-07-15T08:00:00Z",
                  "updatedAt": "2026-07-16T09:30:00.123Z"
                }
              ]
            }
        """.trimIndent()

        val decoded = success(text)

        assertEquals(1, decoded.tours.size)
        assertEquals("0b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60", decoded.tours.single().uuid)
        assertEquals(emptyMap<String, String>(), decoded.tourVehicleUuid)
        assertEquals(emptyList<BackupVehicle>(), decoded.vehicles)
        assertEquals(null, decoded.currentVehicleUuid)

        // Die alten Stellplatz-Felder der Tour (13 Übernachtungen) werden zu einer Übernachtungs-Station.
        val station = decoded.stations.single()
        assertEquals(StationType.OVERNIGHT, station.type)
        assertEquals("Lofoten", station.name)
        assertEquals(LocalDate.of(2026, 7, 1), station.date)
        assertEquals(13, station.nights)
        assertEquals(true, station.pitchAssigned)
        assertEquals(ElectricityBilling.NONE, station.electricityBilling)
        assertEquals(LteQuality.OK, station.lteQuality)
        assertEquals(PitchSlope.SLOPED, station.pitchSlope)
        assertEquals(true, station.levelingBlocksUsed)
        assertEquals(mapOf(station.uuid to decoded.tours.single().uuid), decoded.stationTourUuid)
        assertEquals(emptyMap<String, String>(), decoded.stationVehicleUuid)
    }

    @Test
    fun decode_rejectsInvalidTopLevelValues() {
        listOf(
            "\"mainCurrency\": \"NOK\"" to "\"mainCurrency\": \"XXX1\"",
            "\"exportedAt\": \"2026-10-04T12:00:00Z\"" to "\"exportedAt\": \"gestern\"",
            "\"exportedAt\": \"2026-10-04T12:00:00Z\"" to "\"exportedAt\": \"+999999999-12-31T23:30:00Z\"",
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
            "\"travelDays\": 14" to "\"travelDays\": -1",
            "\"travelDays\": 14" to "\"travelDays\": 99999999999",
            "\"overnightStays\": 13" to "\"overnightStays\": 15",
            "\"distanceKm\": 4210" to "\"distanceKm\": 42.1",
            "\"mapLink\": \"https://maps.example.org/?q=1\"" to "\"mapLink\": \"javascript:alert(1)\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"1234.567\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"-1\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"100000000000.01\"",
            "\"amount\": \"1234.56\"" to "\"amount\": \"1,5\"",
            "\"amount\": \"1500\"" to "\"amount\": \"1500.5\"",
            "\"currency\": \"JPY\"" to "\"currency\": \"NOK\"",
            "\"createdAt\": \"2026-07-15T08:00:00Z\"" to "\"createdAt\": \"2026-07-15\"",
            "\"createdAt\": \"2026-07-15T08:00:00Z\"" to "\"createdAt\": \"+999999999-12-31T23:30:00Z\"",
            "\"startDate\": \"2026-07-01\"" to "\"startDate\": \"1899-12-31\"",
            "\"endDate\": \"2026-07-14\"" to "\"endDate\": \"+10000-01-01\"",
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

    @Test
    fun decode_rejectsDanglingTourVehicleUuid() {
        val result = failure(vehicleEncodedWith("\"vehicleUuid\": \"$vehicleUuid\"", "\"vehicleUuid\": \"00000000-0000-4000-8000-000000000099\""))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, tourNumber = 1), result)
    }

    @Test
    fun decode_rejectsDanglingCurrentVehicleUuid() {
        val result = failure(vehicleEncodedWith("\"currentVehicle\": \"$vehicleUuid\"", "\"currentVehicle\": \"00000000-0000-4000-8000-000000000099\""))

        assertEquals(BackupError.INVALID_DATA, result?.error)
    }

    @Test
    fun decode_rejectsDuplicateVehicleUuid() {
        val twice = vehicleBackup.copy(vehicles = vehicleBackup.vehicles + vehicleBackup.vehicles)

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 2), failure(encodeBackup(twice)))
    }

    @Test
    fun decode_rejectsDuplicateRepairUuid() {
        val twice = vehicleBackup.copy(
            vehicles = listOf(vehicleBackup.vehicles.single().let { it.copy(repairs = it.repairs + it.repairs) }),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1, repairNumber = 2), failure(encodeBackup(twice)))
    }

    @Test
    fun decode_rejectsDuplicateLogEntryUuid() {
        val twice = vehicleBackup.copy(
            vehicles = listOf(vehicleBackup.vehicles.single().let { it.copy(logEntries = it.logEntries + it.logEntries) }),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1, logEntryNumber = 2), failure(encodeBackup(twice)))
    }

    @Test
    fun decode_reportsNumberOfInvalidVehicle() {
        listOf(
            "\"uuid\": \"$vehicleUuid\"" to "\"uuid\": \"keine-uuid\"",
            "\"createdAt\": \"2018-05-01T08:00:00Z\"" to "\"createdAt\": \"gestern\"",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1), failure(vehicleEncodedWith(old, new)))
        }
    }

    @Test
    fun decode_rejectsVehicleFieldsOutOfBounds() {
        val maxLengthCm = (MAX_DIMENSION_M * 100).toInt()
        val maxTireMbar = (MAX_TIRE_PRESSURE_BAR * 1000).toInt()
        val maxTankDl = (MAX_TANK_L * 10).toInt()
        listOf(
            "\"lengthCm\": 500" to "\"lengthCm\": ${maxLengthCm + 1}",
            "\"lengthCm\": 500" to "\"lengthCm\": -1",
            "\"grossWeightKg\": 3500" to "\"grossWeightKg\": ${MAX_WEIGHT_KG + 1}",
            "\"powerKw\": 130" to "\"powerKw\": ${MAX_POWER_KW + 1}",
            "\"tirePressureFrontMbar\": 2500" to "\"tirePressureFrontMbar\": ${maxTireMbar + 1}",
            "\"fuelTankDl\": 900" to "\"fuelTankDl\": ${maxTankDl + 1}",
            "\"batteryCapacityAh\": 200" to "\"batteryCapacityAh\": ${MAX_BATTERY_AH + 1}",
            "\"solarPowerWp\": 400" to "\"solarPowerWp\": ${MAX_SOLAR_WP + 1}",
            "\"purchaseOdometerKm\": 500" to "\"purchaseOdometerKm\": ${MAX_ODOMETER_KM + 1}",
            "\"lastOilChangeOdometerKm\": 12000" to "\"lastOilChangeOdometerKm\": ${MAX_ODOMETER_KM + 1}",
            "\"measuredEmptyWeightKg\": 3020" to "\"measuredEmptyWeightKg\": ${MAX_WEIGHT_KG + 1}",
            "\"measuredEmptyWeightKg\": 3020" to "\"measuredEmptyWeightKg\": -1",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1), failure(vehicleEncodedWith(old, new)))
        }
    }

    @Test
    fun decode_rejectsInvalidVehiclePhoneNumbers() {
        val tooLong = "1".repeat(MAX_PHONE_LENGTH + 1)
        listOf(
            "\"breakdownPhone\": \"+49 89 22 22 22\"" to "\"breakdownPhone\": \"call ADAC\"",
            "\"travelProtectionPhone\": \"+49 30 123456\"" to "\"travelProtectionPhone\": \"$tooLong\"",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1), failure(vehicleEncodedWith(old, new)))
        }
    }

    @Test
    fun decode_acceptsVehiclePhoneNumbersWithFormattingCharacters() {
        val decoded = success(vehicleEncodedWith("\"insurerClaimsPhone\": \"+49 69 5678\"", "\"insurerClaimsPhone\": \"+49 (0)69-5678/0\""))

        assertEquals("+49 (0)69-5678/0", decoded.vehicles.single().vehicle.insurerClaimsPhone)
    }

    @Test
    fun decode_rejectsInvalidRepairValues() {
        listOf(
            "\"description\": \"Bremsen erneuert\"" to "\"description\": \"   \"",
            "\"odometerKm\": 60000" to "\"odometerKm\": ${MAX_ODOMETER_KM + 1}",
            "\"odometerKm\": 60000" to "\"odometerKm\": -1",
            "\"date\": \"2025-06-01\"" to "\"date\": \"keingueltigesdatum\"",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1, repairNumber = 1), failure(vehicleEncodedWith(old, new)))
        }
    }

    @Test
    fun decode_rejectsInvalidLogEntryValues() {
        listOf(
            "\"type\": \"CASSETTE_EMPTIED\"" to "\"type\": \"UNKNOWN\"",
            "\"date\": \"2026-09-01\"" to "\"date\": \"keingueltigesdatum\"",
        ).forEach { (old, new) ->
            assertEquals(new, BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1, logEntryNumber = 1), failure(vehicleEncodedWith(old, new)))
        }
    }

    private val stationBackup = vehicleBackup.copy(
        stations = listOf(station()),
        stationVehicleUuid = mapOf(stationUuid to vehicleUuid),
        stationTourUuid = mapOf(stationUuid to tour().uuid),
    )

    /** Wie [encodedWith], ersetzt aber nur innerhalb des Felds `stations`, damit z. B. `vehicleUuid` nicht mit dem einer Tour kollidiert. */
    private fun stationEncodedWith(old: String, new: String): String = stationsEncodedWith(stationBackup, listOf(old to new))

    /** Wie [stationEncodedWith], wendet aber mehrere Ersetzungen nacheinander innerhalb des Felds `stations` an. */
    private fun stationsEncodedWith(backup: Backup, replacements: List<Pair<String, String>>): String {
        val text = encodeBackup(backup)
        val stationsIndex = text.indexOf("\"stations\"")
        check(stationsIndex >= 0) { "kein stations-Feld in der Sicherung" }
        val head = text.substring(0, stationsIndex)
        val tail = text.substring(stationsIndex)
        return head + applyAll(tail, replacements)
    }

    @Test
    fun roundTrip_keepsStationWithTourAndVehicleUuid() {
        val decoded = success(encodeBackup(stationBackup))

        assertEquals(listOf(station()), decoded.stations)
        assertEquals(mapOf(stationUuid to vehicleUuid), decoded.stationVehicleUuid)
        assertEquals(mapOf(stationUuid to tour().uuid), decoded.stationTourUuid)
    }

    @Test
    fun decode_mapsTheLegacyStationElectricityFlagToBilling() {
        val legacyFields = listOf("\"electricityBilling\": \"BASE_PLUS_METERED\"" to "\"electricityBilling\": null")

        val yes = stationsEncodedWith(stationBackup, legacyFields + ("\"electricityFlatRate\": null" to "\"electricityFlatRate\": \"YES\""))
        assertEquals(ElectricityBilling.FLAT_PER_STAY, success(yes).stations.single().electricityBilling)

        val no = stationsEncodedWith(stationBackup, legacyFields + ("\"electricityFlatRate\": null" to "\"electricityFlatRate\": \"NO\""))
        assertEquals(ElectricityBilling.METERED, success(no).stations.single().electricityBilling)

        val notUsed = stationsEncodedWith(stationBackup, legacyFields + ("\"electricityFlatRate\": null" to "\"electricityFlatRate\": \"NOT_USED\""))
        assertEquals(ElectricityBilling.NONE, success(notUsed).stations.single().electricityBilling)
    }

    @Test
    fun decode_importsUnknownLegacyElectricityFlatRateAsNullBilling() {
        val legacyElectricityStation = station().copy(
            electricityBilling = null,
            electricityCurrency = null,
            electricityBaseFee = null,
            electricityPricePerKwh = null,
            electricityMeterStart = null,
            electricityMeterEnd = null,
        )
        val text = stationsEncodedWith(
            stationBackup.copy(stations = listOf(legacyElectricityStation)),
            listOf("\"electricityFlatRate\": null" to "\"electricityFlatRate\": \"unbekannt\""),
        )

        assertEquals(null, success(text).stations.single().electricityBilling)
    }

    @Test
    fun decode_rejectsElectricityPricePerKwhAboveTheBound() {
        val result = failure(stationEncodedWith("\"electricityPricePerKwh\": \"0.35\"", "\"electricityPricePerKwh\": \"100.01\""))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_rejectsElectricityMeterValueAboveTheBound() {
        val result = failure(stationEncodedWith("\"electricityMeterEnd\": \"135.75\"", "\"electricityMeterEnd\": \"100001\""))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_rejectsTooManyStationCostLines() {
        val tooMany = stationBackup.copy(
            stations = listOf(station().copy(costs = (1..MAX_COSTS_PER_STATION + 1).map { StationCost(CostCategory.OTHER, Money(it.toLong(), eur), "") })),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), failure(encodeBackup(tooMany)))
    }

    @Test
    fun decode_rejectsDuplicateStationCostCategoryAndCurrency() {
        val duplicate = stationBackup.copy(
            stations = listOf(
                station().copy(
                    costs = listOf(StationCost(CostCategory.SUPPLY, Money(100, eur)), StationCost(CostCategory.SUPPLY, Money(200, eur))),
                ),
            ),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), failure(encodeBackup(duplicate)))
    }

    private fun tollStation(uuid: String = stationUuid) = Station(
        uuid = uuid,
        vehicleId = 0,
        tourId = null,
        type = StationType.TOLL,
        date = LocalDate.of(2026, 7, 4),
        name = "A1 Mautstelle",
        tollKind = TollKind.MOTORWAY,
        tollPaymentMethod = "App",
        tollCountry = "AT",
        tollValidFrom = LocalDate.of(2026, 1, 1),
        tollValidUntil = LocalDate.of(2026, 12, 31),
        createdAt = Instant.parse("2026-07-04T18:00:00Z"),
        updatedAt = Instant.parse("2026-07-05T08:00:00Z"),
    )

    private val tollBackup = vehicleBackup.copy(
        stations = listOf(tollStation()),
        stationVehicleUuid = mapOf(stationUuid to vehicleUuid),
        stationTourUuid = emptyMap(),
    )

    @Test
    fun roundTrip_keepsTollStationFields() {
        assertEquals(listOf(tollStation()), success(encodeBackup(tollBackup)).stations)
    }

    @Test
    fun decode_rejectsTollStationWithUnknownCountryCode() {
        val text = encodeBackup(tollBackup).let {
            check("\"tollCountry\": \"AT\"" in it)
            it.replaceFirst("\"tollCountry\": \"AT\"", "\"tollCountry\": \"XX\"")
        }

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), failure(text))
    }

    @Test
    fun decode_rejectsTollStationWithValidUntilBeforeValidFrom() {
        val text = encodeBackup(tollBackup).let {
            check("\"tollValidUntil\": \"2026-12-31\"" in it)
            it.replaceFirst("\"tollValidUntil\": \"2026-12-31\"", "\"tollValidUntil\": \"2025-12-31\"")
        }

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), failure(text))
    }

    @Test
    fun decode_rejectsTollFieldsOnAnOvernightStation() {
        val mixed = tollStation().copy(type = StationType.OVERNIGHT)
        val mixedBackup = vehicleBackup.copy(
            stations = listOf(mixed),
            stationVehicleUuid = mapOf(stationUuid to vehicleUuid),
            stationTourUuid = emptyMap(),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), failure(encodeBackup(mixedBackup)))
    }

    private val stationLinkedLogEntryBackup = stationBackup.copy(logEntryStationUuid = mapOf(logEntryUuid to stationUuid))

    @Test
    fun roundTrip_keepsLogEntryStationLink() {
        val decoded = success(encodeBackup(stationLinkedLogEntryBackup))

        assertEquals(mapOf(logEntryUuid to stationUuid), decoded.logEntryStationUuid)
    }

    @Test
    fun decode_rejectsLogEntryWithUnknownStationUuid() {
        val text = encodeBackup(stationLinkedLogEntryBackup)
        check("\"stationUuid\": \"$stationUuid\"" in text) { "stationUuid nicht in der Sicherung" }
        val replaced = text.replaceFirst("\"stationUuid\": \"$stationUuid\"", "\"stationUuid\": \"00000000-0000-4000-8000-000000000099\"")

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, vehicleNumber = 1, logEntryNumber = 1), failure(replaced))
    }

    @Test
    fun decode_rejectsStationWithUnknownVehicleUuid() {
        val result = failure(stationEncodedWith("\"vehicleUuid\": \"$vehicleUuid\"", "\"vehicleUuid\": \"00000000-0000-4000-8000-000000000099\""))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_rejectsStationWithUnknownTourUuid() {
        val result = failure(
            stationEncodedWith(
                "\"tourUuid\": \"${tour().uuid}\"",
                "\"tourUuid\": \"00000000-0000-4000-8000-000000000099\"",
            ),
        )

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_rejectsDuplicateStationUuid() {
        val twice = stationBackup.copy(stations = stationBackup.stations + stationBackup.stations)

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 2), failure(encodeBackup(twice)))
    }

    @Test
    fun decode_rejectsStationWithTypeSpecificFieldsForTheWrongType() {
        val result = failure(stationEncodedWith("\"type\": \"OVERNIGHT\"", "\"type\": \"SIGHT\""))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_rejectsStationServiceNotAllowedForItsType() {
        val fuelStation = station().copy(
            type = StationType.FUEL,
            nights = null,
            siteKind = null,
            pitchAssigned = null,
            lteQuality = null,
            pitchSlope = null,
            levelingBlocksUsed = null,
            electricityBilling = null,
            electricityCurrency = null,
            electricityBaseFee = null,
            electricityPricePerKwh = null,
            electricityMeterStart = null,
            electricityMeterEnd = null,
            costs = emptyList(),
        )
        val fuelBackup = vehicleBackup.copy(
            stations = listOf(fuelStation),
            stationVehicleUuid = mapOf(stationUuid to vehicleUuid),
            stationTourUuid = emptyMap(),
        )
        val text = encodeBackup(fuelBackup).let {
            check("\"type\": \"FUEL\"" in it)
            it.replaceFirst("\"type\": \"FUEL\"", "\"type\": \"SIGHT\"")
        }

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), failure(text))
    }

    @Test
    fun decode_rejectsStationWithOnlyOneCoordinate() {
        val result = failure(stationEncodedWith("\"longitude\": 13.1023", "\"longitude\": null"))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_rejectsStationWithOutOfRangeCoordinate() {
        val result = failure(stationEncodedWith("\"latitude\": 68.0912", "\"latitude\": 91.0"))

        assertEquals(BackupReadResult.Failure(BackupError.INVALID_DATA, stationNumber = 1), result)
    }

    @Test
    fun decode_importsPartialLegacyPitchFieldsWithMissingOnesAsNull() {
        val decoded = success(encodedWith("\"pitchAssigned\": null", "\"pitchAssigned\": true"))

        val station = decoded.stations.single()
        assertEquals(true, station.pitchAssigned)
        assertEquals(null, station.electricityBilling)
        assertEquals(null, station.lteQuality)
        assertEquals(null, station.pitchSlope)
        assertEquals(null, station.levelingBlocksUsed)
    }

    @Test
    fun decode_importsLegacyPitchFieldsWithUnknownEnumValueAsNull() {
        val text = applyAll(
            encodeBackup(backup),
            listOf(
                "\"pitchAssigned\": null" to "\"pitchAssigned\": true",
                "\"electricityFlatRate\": null" to "\"electricityFlatRate\": \"NOT_USED\"",
                "\"lteQuality\": null" to "\"lteQuality\": \"schlecht\"",
                "\"pitchSlope\": null" to "\"pitchSlope\": \"LEVEL\"",
                "\"levelingBlocksUsed\": null" to "\"levelingBlocksUsed\": false",
            ),
        )

        val station = success(text).stations.single()
        assertEquals(true, station.pitchAssigned)
        assertEquals(ElectricityBilling.NONE, station.electricityBilling)
        assertEquals(null, station.lteQuality)
        assertEquals(PitchSlope.LEVEL, station.pitchSlope)
        assertEquals(false, station.levelingBlocksUsed)
    }

    @Test
    fun decode_ignoresDayTripLegacyPitchWithoutCreatingAStation() {
        val dayTrip = tour(uuid = "6b6f5e2a-6c1d-4e8a-9f3b-2d7c1a4e5f60", destination = "Tagestrip").copy(overnightStays = 0)
        val text = applyAll(
            encodeBackup(backup.copy(tours = listOf(dayTrip))),
            listOf(
                "\"pitchAssigned\": null" to "\"pitchAssigned\": false",
                "\"electricityFlatRate\": null" to "\"electricityFlatRate\": \"NOT_USED\"",
                "\"lteQuality\": null" to "\"lteQuality\": \"GOOD\"",
                "\"pitchSlope\": null" to "\"pitchSlope\": \"SLOPED\"",
                "\"levelingBlocksUsed\": null" to "\"levelingBlocksUsed\": true",
            ),
        )

        val decoded = success(text)

        assertEquals(emptyList<Station>(), decoded.stations)
    }
}
