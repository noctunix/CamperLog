package app.restvolt.camperlog.ui

import android.os.Bundle
import android.os.Parcel
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.domain.CostCategory
import app.restvolt.camperlog.domain.ElectricityBilling
import app.restvolt.camperlog.domain.ElectricityFlatRate
import app.restvolt.camperlog.domain.EUR
import app.restvolt.camperlog.domain.ExchangeRate
import app.restvolt.camperlog.domain.LteQuality
import app.restvolt.camperlog.domain.Money
import app.restvolt.camperlog.domain.PitchSlope
import app.restvolt.camperlog.domain.Repair
import app.restvolt.camperlog.domain.RepairError
import app.restvolt.camperlog.domain.RepairField
import app.restvolt.camperlog.domain.Station
import app.restvolt.camperlog.domain.StationCostInput
import app.restvolt.camperlog.domain.StationError
import app.restvolt.camperlog.domain.StationField
import app.restvolt.camperlog.domain.StationType
import app.restvolt.camperlog.domain.TollKind
import app.restvolt.camperlog.domain.Tour
import app.restvolt.camperlog.domain.TourError
import app.restvolt.camperlog.domain.TourField
import app.restvolt.camperlog.domain.TourType
import app.restvolt.camperlog.domain.VehicleError
import app.restvolt.camperlog.domain.VehicleField
import app.restvolt.camperlog.ui.edit.EditStationViewModel
import app.restvolt.camperlog.ui.edit.EditTourViewModel
import app.restvolt.camperlog.ui.edit.EditVehicleViewModel
import app.restvolt.camperlog.ui.edit.RepairEditViewModel
import app.restvolt.camperlog.ui.rates.RateEditViewModel
import app.restvolt.camperlog.ui.rates.RateError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.Locale

/**
 * Prüft, dass ungespeicherte Formulareingaben ein Beenden des Prozesses im Hintergrund überstehen.
 * Flow-Tests können das nicht nachstellen, weil ein Neuaufbau der Activity die ViewModels behält.
 * Hier schreibt Android den Handle-Inhalt in ein Parcel und baut daraus ein neues ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftRestorationTest {

    private val chf = Currency.getInstance("CHF")
    private val nok = Currency.getInstance("NOK")
    private val locale = { Locale.US }
    private val vehicles = FakeVehicleRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun newTourInputSurvivesProcessDeath() {
        val repository = FakeTourRepository()
        val handle = SavedStateHandle()
        val before = EditTourViewModel(repository, vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle, locale)
        before.onInputChange { it.copy(destination = "Gardasee", notes = "Stellplatz 12") }
        before.onStartDateChange(LocalDate.of(2026, 7, 1))
        before.onCostAmountChange(0, "139.90")
        before.onCostCurrencyChange(0, chf)

        val after = EditTourViewModel(repository, vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle.afterProcessDeath(), locale)

        val state = after.uiState.value
        assertEquals(before.uiState.value.input, state.input)
        assertTrue(state.isDirty)
        assertTrue(state.errors.isEmpty())
    }

    @Test
    fun selectedVehicleSurvivesProcessDeath() {
        val vehicles = FakeVehicleRepository(
            listOf(defaultVehicle(id = 1, name = "Wohnmobil A"), defaultVehicle(id = 2, name = "Wohnmobil B")),
            currentVehicleId = 1,
        )
        val handle = SavedStateHandle()
        val before = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle, locale)
        before.onVehicleChange(2)

        val after = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle.afterProcessDeath(), locale)

        assertEquals(2L, after.uiState.value.input.vehicleId)
    }

    @Test
    fun draftWinsOverStoredTour() {
        val repository = FakeTourRepository(listOf(tour()))
        val handle = SavedStateHandle()
        EditTourViewModel(repository, vehicles, FakeStationRepository(), FakeChecklistRepository(), 1, handle, locale).onInputChange { it.copy(destination = "Ostsee") }

        val after = EditTourViewModel(repository, vehicles, FakeStationRepository(), FakeChecklistRepository(), 1, handle.afterProcessDeath(), locale)

        val state = after.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Ostsee", state.input.destination)
        assertEquals("420", state.input.distanceKm)
        assertTrue(state.isDirty)
    }

    @Test
    fun visibleErrorsSurviveProcessDeath() {
        val handle = SavedStateHandle()
        val before = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle, locale)
        before.save()

        val after = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle.afterProcessDeath(), locale)

        assertEquals(TourError.REQUIRED, after.uiState.value.errors[TourField.START_DATE])
        after.onInputChange { it.copy(startDate = LocalDate.of(2026, 1, 1)) }
        assertFalse(TourField.START_DATE in after.uiState.value.errors)
    }

    @Test
    fun prefilledTravelDaysKeepFollowingDatesAfterRestore() {
        val handle = SavedStateHandle()
        val before = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle, locale)
        before.onStartDateChange(LocalDate.of(2026, 7, 1))
        before.onEndDateChange(LocalDate.of(2026, 7, 3))
        assertEquals("3", before.uiState.value.input.travelDays)

        val after = EditTourViewModel(FakeTourRepository(), vehicles, FakeStationRepository(), FakeChecklistRepository(), 0, handle.afterProcessDeath(), locale)
        after.onEndDateChange(LocalDate.of(2026, 7, 5))

        assertEquals("5", after.uiState.value.input.travelDays)
    }

    @Test
    fun savedTourLeavesNoDraft() {
        val repository = FakeTourRepository(listOf(tour()))
        val handle = SavedStateHandle()
        val before = EditTourViewModel(repository, vehicles, FakeStationRepository(), FakeChecklistRepository(), 1, handle, locale)
        before.onInputChange { it.copy(destination = "Ostsee") }
        before.save()
        assertTrue(before.uiState.value.isSaved)
        assertTrue(handle.keys().isEmpty())

        val after = EditTourViewModel(repository, vehicles, FakeStationRepository(), FakeChecklistRepository(), 1, handle.afterProcessDeath(), locale)

        assertEquals("Ostsee", after.uiState.value.input.destination)
        assertFalse(after.uiState.value.isDirty)
    }

    @Test
    fun untouchedFormStoresNothing() {
        val handle = SavedStateHandle()
        EditTourViewModel(FakeTourRepository(listOf(tour())), vehicles, FakeStationRepository(), FakeChecklistRepository(), 1, handle, locale)
        RateEditViewModel(FakeExchangeRateRepository(), null, handle, { LocalDate.of(2026, 10, 2) }, locale)

        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun newRateInputSurvivesProcessDeath() {
        val repository = FakeExchangeRateRepository(listOf(rate(nok)))
        val handle = SavedStateHandle()
        val before = RateEditViewModel(repository, null, handle, { LocalDate.of(2026, 10, 2) }, locale)
        before.onCurrencyChange(chf)
        before.onRateChange("0.94")
        before.onDateChange(LocalDate.of(2026, 9, 30))
        before.onSourceChange("Bank")

        val after = RateEditViewModel(repository, null, handle.afterProcessDeath(), { LocalDate.of(2026, 10, 3) }, locale)

        val state = after.uiState.value
        assertTrue(state.isNew)
        assertEquals(chf, state.currency)
        assertEquals("0.94", state.rate)
        assertEquals(LocalDate.of(2026, 9, 30), state.date)
        assertEquals("Bank", state.source)
        assertEquals(setOf(EUR, nok), state.unavailable)
        assertTrue(state.isDirty)
    }

    @Test
    fun rateDraftKeepsCurrencyOfExistingRateAndErrors() {
        val repository = FakeExchangeRateRepository(listOf(rate(nok)))
        val handle = SavedStateHandle()
        val before = RateEditViewModel(repository, "NOK", handle, { LocalDate.of(2026, 10, 2) }, locale)
        before.onRateChange("abc")
        before.save()

        val after = RateEditViewModel(repository, "NOK", handle.afterProcessDeath(), { LocalDate.of(2026, 10, 2) }, locale)

        val state = after.uiState.value
        assertFalse(state.isNew)
        assertEquals(nok, state.currency)
        assertEquals("abc", state.rate)
        assertEquals(setOf(RateError.RATE_INVALID), state.errors)
        after.onRateChange("12.5")
        after.save()
        assertEquals(BigDecimal("12.5"), repository.rates.single().perEuro)
        assertTrue(after.uiState.value.isSaved)
    }

    @Test
    fun savedRateLeavesNoDraft() {
        val repository = FakeExchangeRateRepository()
        val handle = SavedStateHandle()
        val before = RateEditViewModel(repository, "CHF", handle, { LocalDate.of(2026, 10, 2) }, locale)
        before.onRateChange("0.94")
        before.save()
        assertTrue(before.uiState.value.isSaved)

        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun newVehicleInputSurvivesProcessDeath() {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val before = EditVehicleViewModel(repository, 0, handle, locale)
        before.onInputChange { it.copy(name = "Wohnmobil", lengthM = "6.36") }

        val after = EditVehicleViewModel(repository, 0, handle.afterProcessDeath(), locale)

        assertEquals(before.uiState.value.input, after.uiState.value.input)
        assertTrue(after.uiState.value.isDirty)
    }

    @Test
    fun newVehicleBreakdownFieldsSurviveProcessDeath() {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val before = EditVehicleViewModel(repository, 0, handle, locale)
        before.onInputChange {
            it.copy(
                measuredEmptyWeightKg = "3020",
                breakdownProvider = "ADAC",
                breakdownPhone = "+49 89 22 22 22",
            )
        }

        val after = EditVehicleViewModel(repository, 0, handle.afterProcessDeath(), locale)

        assertEquals(before.uiState.value.input, after.uiState.value.input)
    }

    @Test
    fun vehicleDraftWinsOverStoredVehicle() {
        val repository = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Alt")))
        val handle = SavedStateHandle()
        EditVehicleViewModel(repository, 1, handle, locale).onInputChange { it.copy(name = "Neu") }

        val after = EditVehicleViewModel(repository, 1, handle.afterProcessDeath(), locale)

        assertEquals("Neu", after.uiState.value.input.name)
        assertTrue(after.uiState.value.isDirty)
    }

    @Test
    fun vehicleVisibleErrorsSurviveProcessDeath() {
        val repository = FakeVehicleRepository()
        val handle = SavedStateHandle()
        val before = EditVehicleViewModel(repository, 0, handle, locale)
        before.save()

        val after = EditVehicleViewModel(repository, 0, handle.afterProcessDeath(), locale)

        assertEquals(VehicleError.REQUIRED, after.uiState.value.errors[VehicleField.NAME])
        after.onInputChange { it.copy(name = "Camper") }
        assertFalse(VehicleField.NAME in after.uiState.value.errors)
    }

    @Test
    fun savedVehicleLeavesNoDraft() {
        val repository = FakeVehicleRepository(listOf(defaultVehicle(id = 1, name = "Alt")))
        val handle = SavedStateHandle()
        val before = EditVehicleViewModel(repository, 1, handle, locale)
        before.onInputChange { it.copy(name = "Neu") }
        before.save()

        assertTrue(before.uiState.value.isSaved)
        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun newRepairInputSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val before = RepairEditViewModel(vehicles, FakeExchangeRateRepository(), vehicles.currentVehicleId, 0, handle, locale)
        before.onInputChange { it.copy(description = "Reifen gewechselt", odometerKm = "42000") }

        val after = RepairEditViewModel(
            vehicles, FakeExchangeRateRepository(), vehicles.currentVehicleId, 0, handle.afterProcessDeath(), locale,
        )

        assertEquals(before.uiState.value.input, after.uiState.value.input)
        assertTrue(after.uiState.value.isDirty)
    }

    @Test
    fun repairDraftWinsOverStoredRepair() {
        val vehicleId = vehicles.currentVehicleId
        val repairId = runBlocking { vehicles.saveRepair(repair(vehicleId, "Alt")) }
        val handle = SavedStateHandle()
        RepairEditViewModel(vehicles, FakeExchangeRateRepository(), vehicleId, repairId, handle, locale)
            .onInputChange { it.copy(description = "Neu") }

        val after = RepairEditViewModel(vehicles, FakeExchangeRateRepository(), vehicleId, repairId, handle.afterProcessDeath(), locale)

        assertEquals("Neu", after.uiState.value.input.description)
        assertTrue(after.uiState.value.isDirty)
    }

    @Test
    fun repairVisibleErrorsSurviveProcessDeath() {
        val handle = SavedStateHandle()
        val before = RepairEditViewModel(vehicles, FakeExchangeRateRepository(), vehicles.currentVehicleId, 0, handle, locale)
        before.onInputChange { it.copy(description = "") }
        before.save()

        val after = RepairEditViewModel(
            vehicles, FakeExchangeRateRepository(), vehicles.currentVehicleId, 0, handle.afterProcessDeath(), locale,
        )

        assertEquals(RepairError.REQUIRED, after.uiState.value.errors[RepairField.DESCRIPTION])
        after.onInputChange { it.copy(description = "Reifen") }
        assertFalse(RepairField.DESCRIPTION in after.uiState.value.errors)
    }

    @Test
    fun savedRepairLeavesNoDraft() {
        val vehicleId = vehicles.currentVehicleId
        val repairId = runBlocking { vehicles.saveRepair(repair(vehicleId, "Alt")) }
        val handle = SavedStateHandle()
        val before = RepairEditViewModel(vehicles, FakeExchangeRateRepository(), vehicleId, repairId, handle, locale)
        before.onInputChange { it.copy(description = "Neu") }
        before.save()

        assertTrue(before.uiState.value.isSaved)
        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun newStationInputSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val before = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = vehicles,
            attachments = FakeAttachmentRepository(),
            fileStore = FakeAttachmentFileStore(),
            stationId = 0,
            savedStateHandle = handle,
        )
        before.onInputChange { it.copy(name = "Camping Moskenes", place = "Moskenes, Norwegen") }
        before.onLocationTextChange("68.0912, 13.1023")

        val after = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = vehicles,
            attachments = FakeAttachmentRepository(),
            fileStore = FakeAttachmentFileStore(),
            stationId = 0,
            savedStateHandle = handle.afterProcessDeath(),
        )

        val state = after.uiState.value
        assertEquals(before.uiState.value.input, state.input)
        assertEquals(68.0912, state.input.latitude)
        assertTrue(state.isDirty)
        assertTrue(state.errors.isEmpty())
    }

    @Test
    fun newStationInputWithCostsElectricityAndTollSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val before = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = vehicles,
            attachments = FakeAttachmentRepository(),
            fileStore = FakeAttachmentFileStore(),
            stationId = 0,
            savedStateHandle = handle,
        )
        before.onInputChange {
            it.copy(
                costs = listOf(StationCostInput(CostCategory.PITCH, "15", EUR, "Platz 12")),
                electricityBilling = ElectricityBilling.METERED,
                electricityCurrency = chf,
                electricityPricePerKwh = "0.45",
                electricityMeterStart = "100",
                electricityMeterEnd = "120",
                type = StationType.TOLL,
                tollKind = TollKind.VIGNETTE,
                tollCountry = "AT",
            )
        }

        val after = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = vehicles,
            attachments = FakeAttachmentRepository(),
            fileStore = FakeAttachmentFileStore(),
            stationId = 0,
            savedStateHandle = handle.afterProcessDeath(),
        )

        assertEquals(before.uiState.value.input, after.uiState.value.input)
    }

    @Test
    fun newStationPendingPhotoSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val fileStore = FakeAttachmentFileStore()
        val before = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = vehicles,
            attachments = FakeAttachmentRepository(),
            fileStore = fileStore,
            stationId = 0,
            savedStateHandle = handle,
        )
        before.onAddPendingPhoto("content://fake/photo.jpg".toUri())

        val after = EditStationViewModel(
            repository = FakeStationRepository(),
            tours = FakeTourRepository(),
            vehicles = vehicles,
            attachments = FakeAttachmentRepository(),
            fileStore = fileStore,
            stationId = 0,
            savedStateHandle = handle.afterProcessDeath(),
        )

        assertEquals(before.uiState.value.pendingPhotos, after.uiState.value.pendingPhotos)
        assertTrue(after.uiState.value.isDirty)
    }

    @Test
    fun stationDraftWinsOverStoredStation() {
        val stations = FakeStationRepository(listOf(station()))
        val handle = SavedStateHandle()
        EditStationViewModel(stations, FakeTourRepository(), vehicles, FakeAttachmentRepository(), FakeAttachmentFileStore(), 1, savedStateHandle = handle)
            .onInputChange { it.copy(name = "Anderer Name") }

        val after = EditStationViewModel(
            stations, FakeTourRepository(), vehicles, FakeAttachmentRepository(), FakeAttachmentFileStore(), 1,
            savedStateHandle = handle.afterProcessDeath(),
        )

        val state = after.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Anderer Name", state.input.name)
        assertTrue(state.isDirty)
    }

    @Test
    fun stationVisibleErrorsSurviveProcessDeath() {
        val handle = SavedStateHandle()
        val before = EditStationViewModel(
            FakeStationRepository(), FakeTourRepository(), vehicles, FakeAttachmentRepository(), FakeAttachmentFileStore(), 0,
            savedStateHandle = handle,
        )
        before.onInputChange { it.copy(date = null) }
        before.save()

        val after = EditStationViewModel(
            FakeStationRepository(), FakeTourRepository(), vehicles, FakeAttachmentRepository(), FakeAttachmentFileStore(), 0,
            savedStateHandle = handle.afterProcessDeath(),
        )

        assertEquals(StationError.REQUIRED, after.uiState.value.errors[StationField.DATE])
        after.onInputChange { it.copy(date = LocalDate.of(2026, 7, 4)) }
        assertFalse(StationField.DATE in after.uiState.value.errors)
    }

    @Test
    fun savedStationLeavesNoDraft() {
        val stations = FakeStationRepository(listOf(station()))
        val handle = SavedStateHandle()
        val before = EditStationViewModel(
            stations, FakeTourRepository(), vehicles, FakeAttachmentRepository(), FakeAttachmentFileStore(), 1,
            savedStateHandle = handle,
        )
        before.onInputChange { it.copy(name = "Neu") }
        before.save()

        assertTrue(before.uiState.value.isSaved)
        assertTrue(handle.keys().isEmpty())
    }

    private fun station() = Station(
        id = 1,
        vehicleId = vehicles.currentVehicleId,
        type = StationType.OVERNIGHT,
        date = LocalDate.of(2026, 7, 4),
        name = "Alt",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun rate(currency: Currency) = ExchangeRate(currency, BigDecimal("11.5"), LocalDate.of(2026, 9, 1), "EZB")

    private fun tour() = Tour(
        id = 1,
        startDate = LocalDate.of(2025, 6, 1),
        endDate = LocalDate.of(2025, 6, 3),
        destination = "Gardasee",
        tourType = TourType.WEEKEND,
        travelDays = 3,
        overnightStays = 2,
        distanceKm = 420,
        costs = listOf(Money(8_990, EUR)),
        notes = "",
        mapLink = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun repair(vehicleId: Long, description: String) = Repair(
        vehicleId = vehicleId,
        date = LocalDate.of(2026, 1, 1),
        description = description,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    /** Schreibt den Handle-Inhalt wie Android beim Beenden des Prozesses in ein Parcel und liest ihn neu. */
    private fun SavedStateHandle.afterProcessDeath(): SavedStateHandle {
        val bundle = Bundle().apply { keys().forEach { key -> putBundle(key, get<Bundle>(key)) } }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            val restored = checkNotNull(parcel.readBundle(javaClass.classLoader))
            return SavedStateHandle(restored.keySet().associateWith { restored.getBundle(it) })
        } finally {
            parcel.recycle()
        }
    }
}
