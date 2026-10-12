package app.restvolt.camperlog.ui.settings

import app.restvolt.camperlog.domain.CoordinateSource
import app.restvolt.camperlog.domain.FakeLocationPermissionGate
import app.restvolt.camperlog.domain.FakeLocationProvider
import app.restvolt.camperlog.domain.HomeLocation
import app.restvolt.camperlog.domain.LocationFix
import app.restvolt.camperlog.ui.FakeHomeLocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeLocationViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeHomeLocationRepository,
        provider: FakeLocationProvider = FakeLocationProvider(),
    ) = HomeLocationViewModel(repository, provider, FakeLocationPermissionGate())

    @Test
    fun addNew_createsAnEmptyEntry() = runTest {
        val repository = FakeHomeLocationRepository()
        val viewModel = viewModel(repository)

        viewModel.addNew()
        advanceUntilIdle()

        val saved = repository.locations.single()
        assertEquals("", saved.name)
        assertNull(saved.latitude)
    }

    @Test
    fun delete_removesTheEntryAgain() = runTest {
        val existing = HomeLocation(id = 1, name = "Elternhaus")
        val repository = FakeHomeLocationRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.delete(existing.id)
        advanceUntilIdle()

        assertEquals(emptyList<HomeLocation>(), repository.locations)
    }

    /** Ein gefundener Fix landet nur in dem Eintrag, für den [HomeLocationViewModel.startCapture] zuletzt aufgerufen wurde. */
    @Test
    fun capturedFix_updatesOnlyTheTargetedEntry() = runTest {
        val first = HomeLocation(id = 1, name = "Erstes Zuhause")
        val second = HomeLocation(id = 2, name = "Zweites Zuhause")
        val repository = FakeHomeLocationRepository(listOf(first, second))
        val fix = LocationFix(52.52, 13.405, accuracyM = 8)
        val viewModel = viewModel(repository, FakeLocationProvider(freshFix = fix))

        viewModel.startCapture(second.id)
        viewModel.locationCapture.onButtonTapped { false }
        advanceUntilIdle()

        val updatedFirst = repository.locations.single { it.id == first.id }
        val updatedSecond = repository.locations.single { it.id == second.id }
        assertNull(updatedFirst.latitude)
        assertEquals(52.52, updatedSecond.latitude)
        assertEquals(13.405, updatedSecond.longitude)
        assertEquals(CoordinateSource.GPS, updatedSecond.coordinateSource)
    }
}
