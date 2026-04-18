package com.gasstation.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `watchlist exposes watched summaries from repository`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val station = Station(
            id = "station-1",
            name = "Gangnam First",
            brand = Brand.GSC,
            price = MoneyWon(1680),
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "latitude" to "37.498095",
                "longitude" to "127.027610",
            ),
        )
        val viewModel = WatchlistViewModel(
            observeWatchlist = ObserveWatchlistUseCase(
                FakeWatchlistRepository(
                    listOf(
                        WatchedStationSummary(
                            station = station,
                            priceDelta = StationPriceDelta.Decreased(20),
                            lastSeenAt = null,
                        ),
                    ),
                ),
            ),
            savedStateHandle = savedStateHandle,
        )

        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }

        advanceUntilIdle()

        assertEquals("station-1", viewModel.uiState.value.stations.single().id)

        collectionJob.cancel()
        advanceUntilIdle()
    }
}

private class FakeWatchlistRepository(
    private val summaries: List<WatchedStationSummary>,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> =
        flowOf(
            StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
            ),
        )

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> = flowOf(summaries)

    override suspend fun refreshNearbyStations(query: StationQuery) {
        error("refreshNearbyStations is not used in watchlist tests")
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) {
        error("updateWatchState is not used in watchlist tests")
    }
}
