package app.restvolt.camperlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.restvolt.camperlog.backup.Backup
import app.restvolt.camperlog.backup.ImportMode
import app.restvolt.camperlog.backup.ImportResult
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomBackupImporterTest {

    private val nok = Currency.getInstance("NOK")
    private val sek = Currency.getInstance("SEK")

    private lateinit var db: CamperLogDatabase
    private lateinit var tours: RoomTourRepository
    private lateinit var rates: RoomExchangeRateRepository
    private lateinit var importer: RoomBackupImporter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CamperLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tours = RoomTourRepository(db.tourDao())
        rates = RoomExchangeRateRepository(db.exchangeRateDao())
        importer = RoomBackupImporter(db)
    }

    @After
    fun tearDown() = db.close()

    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    private fun tour(n: Int, destination: String = "Ziel $n", updatedAt: String = "2026-07-10T10:00:00Z") = Tour(
        uuid = uuid(n),
        startDate = LocalDate.of(2026, 7, n),
        endDate = LocalDate.of(2026, 7, n + 1),
        destination = destination,
        tourType = TourType.VACATION,
        travelDays = 2,
        overnightStays = 1,
        distanceKm = 100,
        costs = listOf(Money(1000, EUR), Money(5000, nok)),
        pitchAssigned = false,
        electricityFlatRate = ElectricityFlatRate.NOT_USED,
        lteQuality = LteQuality.OK,
        pitchSlope = PitchSlope.SLOPED,
        levelingBlocksUsed = false,
        notes = "",
        mapLink = null,
        createdAt = Instant.parse("2026-07-01T10:00:00Z"),
        updatedAt = Instant.parse(updatedAt),
    )

    private fun rate(currency: Currency, perEuro: String, date: String) =
        ExchangeRate(currency, BigDecimal(perEuro), LocalDate.parse(date), "EZB")

    private fun backup(tours: List<Tour>, rates: List<ExchangeRate> = emptyList(), main: Currency = sek) =
        Backup(Instant.parse("2026-10-04T12:00:00Z"), main, rates, tours)

    /** Gespeicherte Touren ohne Datenbank-id, damit sie mit Sicherungs-Touren vergleichbar sind. */
    private suspend fun storedTours() = tours.allTours().map { it.copy(id = 0) }

    private suspend fun seed(vararg seeded: Tour) = seeded.forEach { tours.restore(it) }

    @Test
    fun merge_addsNewTours_updatesNewer_keepsNewerOrEqualLocal() = runTest {
        seed(
            tour(1, "Lokal alt", updatedAt = "2026-07-10T10:00:00Z"),
            tour(2, "Lokal neu", updatedAt = "2026-07-20T10:00:00Z"),
            tour(3, "Lokal gleich"),
        )

        val result = importer.import(
            backup(
                listOf(
                    tour(1, "Import neu", updatedAt = "2026-07-15T10:00:00Z"),
                    tour(2, "Import alt", updatedAt = "2026-07-15T10:00:00Z"),
                    tour(3, "Import gleich"),
                    tour(4, "Import zusätzlich"),
                ),
            ),
            ImportMode.MERGE,
        )

        assertEquals(ImportResult(addedTours = 1, updatedTours = 1, unchangedTours = 2, importedRates = 0), result)
        assertEquals(
            listOf("Import neu", "Lokal neu", "Lokal gleich", "Import zusätzlich"),
            storedTours().map { it.destination },
        )
        assertEquals(tour(1, "Import neu", updatedAt = "2026-07-15T10:00:00Z"), storedTours().first())
    }

    @Test
    fun merge_updateKeepsLocalIdAndReplacesCosts() = runTest {
        seed(tour(1))
        val id = tours.allTours().single().id
        val newer = tour(1, updatedAt = "2026-08-01T00:00:00Z").copy(costs = listOf(Money(42, sek)))

        importer.import(backup(listOf(newer)), ImportMode.MERGE)

        val stored = tours.allTours().single()
        assertEquals(id, stored.id)
        assertEquals(listOf(Money(42, sek)), stored.costs)
    }

    @Test
    fun merge_takesOnlyNewerRates_andKeepsMainCurrency() = runTest {
        rates.setMainCurrency(nok)
        rates.saveRate(rate(nok, "11.5", "2026-09-01"))
        rates.saveRate(rate(sek, "11.0", "2026-10-01"))

        val result = importer.import(
            backup(
                emptyList(),
                rates = listOf(rate(nok, "11.7", "2026-09-15"), rate(sek, "10.0", "2026-09-01")),
                main = sek,
            ),
            ImportMode.MERGE,
        )

        assertEquals(1, result.importedRates)
        assertEquals(listOf("11.7", "11.0"), rates.observeRates().first().map { it.perEuro.toPlainString() })
        assertEquals(nok, rates.observeMainCurrency().first())
    }

    @Test
    fun replace_removesEverythingNotInBackup() = runTest {
        seed(tour(1, "Lokal"), tour(2))
        rates.setMainCurrency(nok)
        rates.saveRate(rate(nok, "11.5", "2026-09-01"))
        val imported = listOf(tour(2, "Import", updatedAt = "2026-01-01T00:00:00Z"), tour(5))

        val result = importer.import(backup(imported, rates = listOf(rate(sek, "11.2", "2026-01-01"))), ImportMode.REPLACE)

        assertEquals(ImportResult(addedTours = 2, updatedTours = 0, unchangedTours = 0, importedRates = 1), result)
        assertEquals(imported, storedTours())
        assertEquals(listOf(sek), rates.observeRates().first().map { it.currency })
        assertEquals(sek, rates.observeMainCurrency().first())
    }

    @Test
    fun failure_rollsBackEverything() = runTest {
        seed(tour(1, "Lokal"))
        rates.saveRate(rate(nok, "11.5", "2026-09-01"))
        // Gleiche UUID zweimal verletzt den eindeutigen Index; decodeBackup würde das vorher ablehnen.
        val broken = backup(listOf(tour(7), tour(7)), rates = listOf(rate(sek, "11.2", "2026-01-01")))

        val failed = runCatching { importer.import(broken, ImportMode.REPLACE) }

        assertTrue(failed.isFailure)
        assertEquals(listOf("Lokal"), storedTours().map { it.destination })
        assertEquals(listOf(nok), rates.observeRates().first().map { it.currency })
    }
}
