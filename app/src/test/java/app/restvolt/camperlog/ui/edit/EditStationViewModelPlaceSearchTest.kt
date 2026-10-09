package app.restvolt.camperlog.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.FakePlaceSearchProvider
import app.restvolt.camperlog.domain.PlaceSearchHit
import app.restvolt.camperlog.domain.PlaceSearchResult
import app.restvolt.camperlog.domain.PlaceSearchState
import app.restvolt.camperlog.ui.FakeAttachmentFileStore
import app.restvolt.camperlog.ui.FakeAttachmentRepository
import app.restvolt.camperlog.ui.FakeStationRepository
import app.restvolt.camperlog.ui.FakeTourRepository
import app.restvolt.camperlog.ui.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Übernahme eines Treffers der Ortssuche ins Stationsformular. Robolectric, weil jede
 * Eingabeänderung den Entwurf über [androidx.lifecycle.SavedStateHandle] sichert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditStationViewModelPlaceSearchTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val hit = PlaceSearchHit(
        displayName = "Reine, Moskenes, Nordland, Norway",
        category = "place",
        type = "village",
        latitude = 68.0912,
        longitude = 13.1023,
    )

    private fun viewModel(provider: FakePlaceSearchProvider) = EditStationViewModel(
        repository = FakeStationRepository(),
        tours = FakeTourRepository(),
        vehicles = FakeVehicleRepository(),
        attachments = FakeAttachmentRepository(),
        fileStore = FakeAttachmentFileStore(),
        stationId = 0,
        placeSearchProvider = provider,
        savedStateHandle = SavedStateHandle(),
        locale = { Locale.US },
    )

    @Test
    fun onSearchPlace_passesTheQueryAndTheAppLanguage() = runTest(dispatcher) {
        val provider = FakePlaceSearchProvider(PlaceSearchResult.Success(listOf(hit)))
        val viewModel = viewModel(provider)

        viewModel.onSearchPlace("Reine")
        advanceUntilIdle()

        assertEquals(listOf("Reine" to "en"), provider.requests)
    }

    @Test
    fun onPlaceSearchPick_fillsCoordinatesAndThePlaceWhenItWasEmpty() = runTest(dispatcher) {
        val viewModel = viewModel(FakePlaceSearchProvider(PlaceSearchResult.Success(listOf(hit))))

        viewModel.onPlaceSearchPick(hit)

        val input = viewModel.uiState.value.input
        assertEquals(68.0912, input.latitude)
        assertEquals(13.1023, input.longitude)
        assertEquals(CoordinateSource.ENTERED, input.coordinateSource)
        assertEquals(hit.displayName, input.place)
        assertEquals(PlaceSearchState.Ready, viewModel.placeSearch.state.value)
    }

    @Test
    fun onPlaceSearchPick_withANonEmptyPlace_keepsTheTypedText() = runTest(dispatcher) {
        val viewModel = viewModel(FakePlaceSearchProvider(PlaceSearchResult.Success(listOf(hit))))
        viewModel.onInputChange { it.copy(place = "My own label") }

        viewModel.onPlaceSearchPick(hit)

        val input = viewModel.uiState.value.input
        assertEquals("My own label", input.place)
        assertEquals(68.0912, input.latitude)
        assertEquals(13.1023, input.longitude)
    }
}
