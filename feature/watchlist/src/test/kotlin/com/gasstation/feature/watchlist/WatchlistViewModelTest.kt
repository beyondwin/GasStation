package com.gasstation.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import com.gasstation.domain.station.usecase.RemoveWatchedStationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `watchlist remains loading until fuel preference is ready`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val preferences = MutableSharedFlow<UserPreferences>()
        val repository = RecordingWatchlistRepository(listOf(summary()))
        val viewModel = watchlistViewModel(repository, preferences)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadFailed)
        assertNull(viewModel.uiState.value.fuelType)
        assertEquals(emptyList<WatchlistQuery>(), repository.queries)
    }

    @Test
    fun `watchlist exposes selected fuel summaries after preferences are ready`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = RecordingWatchlistRepository(listOf(summary()))
        val viewModel = watchlistViewModel(repository, MutableStateFlow(UserPreferences.default()))
        val collectionJob = collect(viewModel)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(FuelType.GASOLINE, viewModel.uiState.value.fuelType)
        assertEquals("station-1", viewModel.uiState.value.stations.single().id)
        assertEquals(FuelType.GASOLINE, repository.queries.single().fuelType)
        collectionJob.cancel()
    }

    @Test
    fun `watchlist re-queries every row when selected fuel changes`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val preferences = MutableStateFlow(UserPreferences.default())
        val repository = RecordingWatchlistRepository(listOf(summary()))
        val viewModel = watchlistViewModel(repository, preferences)
        val collectionJob = collect(viewModel)
        advanceUntilIdle()

        preferences.value = preferences.value.copy(fuelType = FuelType.DIESEL)
        advanceUntilIdle()

        assertEquals(
            listOf(FuelType.GASOLINE, FuelType.DIESEL),
            repository.queries.map { it.fuelType },
        )
        assertEquals(FuelType.DIESEL, viewModel.uiState.value.fuelType)
        collectionJob.cancel()
    }

    @Test
    fun `failed fuel preferences expose retryable load failure`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = RecordingWatchlistRepository(emptyList())
        val viewModel = watchlistViewModel(
            repository = repository,
            observePreferences = ObserveUserPreferencesUseCase {
                flow { error("settings failed") }
            },
        )
        val collectionJob = collect(viewModel)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.loadFailed)
        assertNull(viewModel.uiState.value.fuelType)
        assertEquals(emptyList<WatchlistQuery>(), repository.queries)
        collectionJob.cancel()
    }

    @Test
    fun `retry restarts failed fuel preference observation`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = RecordingWatchlistRepository(listOf(summary()))
        var attempts = 0
        val viewModel = watchlistViewModel(
            repository = repository,
            observePreferences = ObserveUserPreferencesUseCase {
                attempts += 1
                if (attempts == 1) flow { error("settings failed") } else flowOf(UserPreferences.default())
            },
        )
        val collectionJob = collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(WatchlistAction.RetryLoad)
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(FuelType.GASOLINE, viewModel.uiState.value.fuelType)
        assertEquals("station-1", viewModel.uiState.value.stations.single().id)
        collectionJob.cancel()
    }

    @Test
    fun `watchlist logs compare viewed once after data is displayed`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val analytics = RecordingStationEventLogger()
        val repository = RecordingWatchlistRepository(listOf(summary()))
        val viewModel = watchlistViewModel(
            repository = repository,
            preferences = MutableStateFlow(UserPreferences.default()),
            analytics = analytics,
        )
        val collectionJob = collect(viewModel)
        advanceUntilIdle()

        repository.summaries.value = listOf(summary(name = "Updated"))
        advanceUntilIdle()

        assertEquals(listOf(StationEvent.CompareViewed(count = 1)), analytics.events)
        collectionJob.cancel()
    }

    @Test
    fun `watchlist still displays summaries when compare viewed logging fails`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = RecordingWatchlistRepository(listOf(summary()))
        val viewModel = watchlistViewModel(
            repository = repository,
            preferences = MutableStateFlow(UserPreferences.default()),
            analytics = ThrowingStationEventLogger(),
        )
        val collectionJob = collect(viewModel)
        advanceUntilIdle()

        assertEquals("station-1", viewModel.uiState.value.stations.single().id)
        collectionJob.cancel()
    }

    @Test
    fun `remove action uses saved identity even when selected fuel price is unavailable`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = RecordingWatchlistRepository(
            listOf(summary(price = null, priceDelta = StationPriceDelta.Unavailable)),
        )
        val analytics = RecordingStationEventLogger()
        val viewModel = watchlistViewModel(
            repository = repository,
            preferences = MutableStateFlow(UserPreferences.default()),
            analytics = analytics,
        )
        val collectionJob = collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(WatchlistAction.RemoveClicked("station-1"))
        advanceUntilIdle()

        assertEquals(listOf("station-1"), repository.removedStationIds)
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
    fun `remove still succeeds when watch toggled logging fails`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = RecordingWatchlistRepository(listOf(summary()))
        val viewModel = watchlistViewModel(
            repository = repository,
            preferences = MutableStateFlow(UserPreferences.default()),
            analytics = ThrowingStationEventLogger(),
        )
        val collectionJob = collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(WatchlistAction.RemoveClicked("station-1"))
        advanceUntilIdle()

        assertEquals(listOf("station-1"), repository.removedStationIds)
        assertEquals(0, viewModel.uiState.value.summary.count)
        collectionJob.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.collect(viewModel: WatchlistViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collectLatest { }
        }
}

private fun watchlistViewModel(
    repository: RecordingWatchlistRepository,
    preferences: Flow<UserPreferences>,
    analytics: StationEventLogger = RecordingStationEventLogger(),
): WatchlistViewModel = watchlistViewModel(
    repository = repository,
    observePreferences = ObserveUserPreferencesUseCase { preferences },
    analytics = analytics,
)

private fun watchlistViewModel(
    repository: RecordingWatchlistRepository,
    observePreferences: ObserveUserPreferencesUseCase,
    analytics: StationEventLogger = RecordingStationEventLogger(),
): WatchlistViewModel = WatchlistViewModel(
    observeWatchlist = ObserveWatchlistUseCase(repository),
    observeUserPreferences = observePreferences,
    removeWatchedStation = RemoveWatchedStationUseCase(repository),
    savedStateHandle = SavedStateHandle(
        mapOf("latitude" to "37.498095", "longitude" to "127.027610"),
    ),
    stationEventLogger = analytics,
)

private fun summary(
    name: String = "Gangnam First",
    price: MoneyWon? = MoneyWon(1_680),
    priceDelta: StationPriceDelta = StationPriceDelta.Unchanged,
) = WatchedStationSummary(
    id = "station-1",
    name = name,
    brand = Brand.GSC,
    price = price,
    distance = DistanceMeters(300),
    coordinates = Coordinates(37.498095, 127.027610),
    priceDelta = priceDelta,
    lastSeenAt = null,
)

private class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}

private class ThrowingStationEventLogger : StationEventLogger {
    override fun log(event: StationEvent): Unit = throw IllegalStateException("analytics failed")
}

private class RecordingWatchlistRepository(initial: List<WatchedStationSummary>) : StationRepository {
    val summaries = MutableStateFlow(initial)
    val queries = mutableListOf<WatchlistQuery>()
    val removedStationIds = mutableListOf<String>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flowOf(
        StationSearchResult(
            stations = emptyList(),
            freshness = StationFreshness.Stale,
            fetchedAt = null,
            hasCachedSnapshot = false,
        ),
    )

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> {
        queries += query
        return summaries
    }

    override suspend fun refreshNearbyStations(query: StationQuery) = Unit

    override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit

    override suspend fun removeWatchedStation(stationId: String) {
        removedStationIds += stationId
        summaries.value = summaries.value.filterNot { it.id == stationId }
    }
}
