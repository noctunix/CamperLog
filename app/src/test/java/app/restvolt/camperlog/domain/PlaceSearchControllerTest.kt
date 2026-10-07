package app.restvolt.camperlog.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Zustandsautomat der Ortssuche, mit einem Fake statt Netzwerk. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchControllerTest {

    private val hit = PlaceSearchHit(
        displayName = "Reine, Moskenes, Nordland, Norway",
        category = "place",
        type = "village",
        latitude = 68.0912,
        longitude = 13.1023,
    )

    @Test
    fun search_success_reachesResultsWithTheHits() = runTest {
        val controller = PlaceSearchController(FakePlaceSearchProvider(PlaceSearchResult.Success(listOf(hit))), this)

        controller.search("Reine", "de")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.Results(listOf(hit)), controller.state.value)
    }

    @Test
    fun search_successWithoutHits_showsEmpty() = runTest {
        val controller = PlaceSearchController(FakePlaceSearchProvider(PlaceSearchResult.Success(emptyList())), this)

        controller.search("Nonexistentplace", "de")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.Empty, controller.state.value)
    }

    @Test
    fun search_offline_showsOffline() = runTest {
        val controller = PlaceSearchController(FakePlaceSearchProvider(PlaceSearchResult.Offline), this)

        controller.search("Reine", "de")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.Offline, controller.state.value)
    }

    @Test
    fun search_rateLimited_showsServiceUnavailable() = runTest {
        val controller = PlaceSearchController(FakePlaceSearchProvider(PlaceSearchResult.RateLimited), this)

        controller.search("Reine", "de")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.ServiceUnavailable, controller.state.value)
    }

    @Test
    fun search_genericError_showsServiceUnavailable() = runTest {
        val controller = PlaceSearchController(FakePlaceSearchProvider(PlaceSearchResult.Error), this)

        controller.search("Reine", "de")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.ServiceUnavailable, controller.state.value)
    }

    @Test
    fun search_blankQuery_doesNothing() = runTest {
        val provider = FakePlaceSearchProvider(PlaceSearchResult.Success(listOf(hit)))
        val controller = PlaceSearchController(provider, this)

        controller.search("   ", "de")
        advanceUntilIdle()

        assertEquals(0, provider.requests.size)
        assertEquals(PlaceSearchState.Ready, controller.state.value)
    }

    @Test
    fun search_trimsAndPassesTheQueryAndLanguage() = runTest {
        val provider = FakePlaceSearchProvider(PlaceSearchResult.Success(listOf(hit)))
        val controller = PlaceSearchController(provider, this)

        controller.search("  Reine  ", "de")
        advanceUntilIdle()

        assertEquals(listOf("Reine" to "de"), provider.requests)
    }

    @Test
    fun reset_returnsToReady() = runTest {
        val controller = PlaceSearchController(FakePlaceSearchProvider(PlaceSearchResult.Offline), this)
        controller.search("Reine", "de")
        advanceUntilIdle()

        controller.reset()

        assertEquals(PlaceSearchState.Ready, controller.state.value)
    }
}
