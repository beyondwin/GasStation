package com.gasstation.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            updateWatchState = UpdateWatchStateUseCase(FakeWatchlistRepository(emptyList())),
            savedStateHandle = savedStateHandle,
            stationEventLogger = RecordingStationEventLogger(),
        )

        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }

        advanceUntilIdle()

        assertEquals("station-1", viewModel.uiState.value.stations.single().id)

        collectionJob.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `watchlist logs compare viewed once after data is displayed`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val analytics = RecordingStationEventLogger()
        val station = Station(
            id = "station-1",
            name = "Gangnam First",
            brand = Brand.GSC,
            price = MoneyWon(1680),
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
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
            updateWatchState = UpdateWatchStateUseCase(FakeWatchlistRepository(emptyList())),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "latitude" to "37.498095",
                    "longitude" to "127.027610",
                ),
            ),
            stationEventLogger = analytics,
        )

        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }

        advanceUntilIdle()
        collectionJob.cancel()
        advanceUntilIdle()

        assertEquals(listOf(StationEvent.CompareViewed(count = 1)), analytics.events)
    }

    @Test
    fun `watchlist still displays summaries when compare viewed logging fails`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val station = Station(
            id = "station-1",
            name = "Gangnam First",
            brand = Brand.GSC,
            price = MoneyWon(1680),
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
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
            updateWatchState = UpdateWatchStateUseCase(FakeWatchlistRepository(emptyList())),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "latitude" to "37.498095",
                    "longitude" to "127.027610",
                ),
            ),
            stationEventLogger = ThrowingStationEventLogger(),
        )

        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }

        advanceUntilIdle()

        assertEquals("station-1", viewModel.uiState.value.stations.single().id)

        collectionJob.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `remove action writes false through update watch state use case`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val station = Station(
            id = "station-1",
            name = "Gangnam First",
            brand = Brand.GSC,
            price = MoneyWon(1680),
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
        )
        val repository = MutableWatchlistRepository(
            listOf(WatchedStationSummary(station, StationPriceDelta.Unchanged, null)),
        )
        val analytics = RecordingStationEventLogger()
        val viewModel = WatchlistViewModel(
            observeWatchlist = ObserveWatchlistUseCase(repository),
            updateWatchState = UpdateWatchStateUseCase(repository),
            savedStateHandle = SavedStateHandle(
                mapOf("latitude" to "37.498095", "longitude" to "127.027610"),
            ),
            stationEventLogger = analytics,
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }
        advanceUntilIdle()

        viewModel.onAction(WatchlistAction.RemoveClicked("station-1"))
        advanceUntilIdle()

        assertEquals(listOf("station-1" to false), repository.watchUpdates)
        assertEquals(emptyList<WatchlistItemUiModel>(), viewModel.uiState.value.stations)
        assertEquals(0, viewModel.uiState.value.summary.count)
        assertEquals(
            listOf(
                StationEvent.CompareViewed(count = 1),
                StationEvent.WatchToggled(stationId = "station-1", watched = false),
            ),
            analytics.events,
        )
        collectionJob.cancel()
    }

    @Test
    fun `remove action ignores an item no longer in the observed watchlist`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = MutableWatchlistRepository(emptyList())
        val viewModel = WatchlistViewModel(
            observeWatchlist = ObserveWatchlistUseCase(repository),
            updateWatchState = UpdateWatchStateUseCase(repository),
            savedStateHandle = SavedStateHandle(
                mapOf("latitude" to 37.498095, "longitude" to 127.027610),
            ),
            stationEventLogger = RecordingStationEventLogger(),
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }
        advanceUntilIdle()

        viewModel.onAction(WatchlistAction.RemoveClicked("missing"))
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Boolean>>(), repository.watchUpdates)
        collectionJob.cancel()
    }

    @Test
    fun `remove still succeeds when watch toggled logging fails`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val station = Station(
            id = "station-1",
            name = "Gangnam First",
            brand = Brand.GSC,
            price = MoneyWon(1680),
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
        )
        val repository = MutableWatchlistRepository(
            listOf(WatchedStationSummary(station, StationPriceDelta.Unchanged, null)),
        )
        val viewModel = WatchlistViewModel(
            observeWatchlist = ObserveWatchlistUseCase(repository),
            updateWatchState = UpdateWatchStateUseCase(repository),
            savedStateHandle = SavedStateHandle(
                mapOf("latitude" to "37.498095", "longitude" to "127.027610"),
            ),
            stationEventLogger = ThrowingStationEventLogger(),
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }
        advanceUntilIdle()

        viewModel.onAction(WatchlistAction.RemoveClicked("station-1"))
        advanceUntilIdle()

        assertEquals(listOf("station-1" to false), repository.watchUpdates)
        assertEquals(0, viewModel.uiState.value.summary.count)
        collectionJob.cancel()
    }
}

private class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}

private class ThrowingStationEventLogger : StationEventLogger {
    override fun log(event: StationEvent): Unit = throw IllegalStateException("analytics failed")
}

private class FakeWatchlistRepository(private val summaries: List<WatchedStationSummary>) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flowOf(
        StationSearchResult(
            stations = emptyList(),
            freshness = StationFreshness.Stale,
            fetchedAt = null,
            hasCachedSnapshot = false,
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

private class MutableWatchlistRepository(initial: List<WatchedStationSummary>) : StationRepository {
    private val summaries = MutableStateFlow(initial)
    val watchUpdates = mutableListOf<Pair<String, Boolean>>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flowOf(
        StationSearchResult(
            stations = emptyList(),
            freshness = StationFreshness.Stale,
            fetchedAt = null,
            hasCachedSnapshot = false,
        ),
    )

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> = summaries

    override suspend fun refreshNearbyStations(query: StationQuery) {
        error("refreshNearbyStations is not used in watchlist tests")
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) {
        watchUpdates += station.id to watched
        if (!watched) summaries.value = summaries.value.filterNot { it.station.id == station.id }
    }
}
