package com.gasstation.feature.stationlist

import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.time.Instant

internal class FakeStationRepository(
    result: StationSearchResult,
    var refreshFailure: Throwable? = null,
    useObservedResultsFlow: Boolean = false,
    initialObservedResult: StationSearchResult? = result,
    private val refreshStarted: CompletableDeferred<Unit>? = null,
    private val releaseRefresh: CompletableDeferred<Unit>? = null,
    var watchMutationResult: WatchMutationResult = WatchMutationResult.Committed,
    var watchMutationFailure: Throwable? = null,
) : StationRepository {
    private val state = MutableStateFlow(result)
    private val observedResults =
        if (useObservedResultsFlow) {
            MutableSharedFlow<StationSearchResult>(
                replay = if (initialObservedResult != null) 1 else 0,
                extraBufferCapacity = 1,
            ).also { flow ->
                initialObservedResult?.let(flow::tryEmit)
            }
        } else {
            null
        }

    val refreshedQueries = mutableListOf<StationQuery>()
    val persistedRefreshQueries = mutableListOf<StationQuery>()
    val observedQueries = mutableListOf<StationQuery>()
    val watchStateUpdates = mutableListOf<Pair<String, Boolean>>()

    fun emitObservedResult(result: StationSearchResult) {
        checkNotNull(observedResults) { "Observed results flow is not enabled for this fake repository." }
            .tryEmit(result)
    }

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        observedQueries += query
        return observedResults ?: state
    }

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshStarted?.complete(Unit)
        releaseRefresh?.await()
        refreshFailure?.let { throw it }
        persistedRefreshQueries += query
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult {
        watchStateUpdates += station.id to watched
        watchMutationFailure?.let { throw it }
        return watchMutationResult
    }

    override suspend fun removeWatchedStation(stationId: String): WatchMutationResult = WatchMutationResult.Committed
}

internal class FakeLocationRepository(
    private val availability: Flow<Boolean> = MutableStateFlow(true),
    private val result: LocationLookupResult = LocationLookupResult.Success(
        Coordinates(37.498095, 127.027610),
    ),
    private val addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Unavailable,
    private val resultForPermission: ((LocationPermissionState) -> LocationLookupResult)? = null,
    private val addressResultForCoordinates: ((Coordinates) -> LocationAddressLookupResult)? = null,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult =
        resultForPermission?.invoke(permissionState) ?: result

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
        addressResultForCoordinates?.invoke(coordinates) ?: addressResult
}

internal class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}

internal class ThrowingStationEventLogger : StationEventLogger {
    override fun log(event: StationEvent): Unit = throw IllegalStateException("analytics failed")
}

internal data class StationListViewModelFixture(
    val viewModel: StationListViewModel,
    val locationStateMachine: LocationStateMachine,
    val refreshCoordinator: RefreshCoordinator,
    val searchOrchestrator: StationSearchOrchestrator,
    val commandQueue: StationListCommandQueue,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.stationListViewModelFixture(
    repository: StationRepository,
    settingsFixture: SettingsUseCaseTestFixture,
    locationRepository: LocationRepository,
    analytics: StationEventLogger = RecordingStationEventLogger(),
    searchOrchestrator: StationSearchOrchestrator? = null,
): StationListViewModelFixture {
    val locationStateMachine = LocationStateMachine(
        getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
        getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
        observeAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    )
    val resolvedSearchOrchestrator = searchOrchestrator ?: StationSearchOrchestrator(
        observeNearbyStations = ObserveNearbyStationsUseCase(repository),
    )
    val commandQueue = StationListCommandQueue()
    val refreshCoordinator = RefreshCoordinator(
        locationStateMachine = locationStateMachine,
        refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
    )
    val viewModel = StationListViewModel(
        searchOrchestrator = resolvedSearchOrchestrator,
        updateWatchState = UpdateWatchStateUseCase(repository),
        observeUserPreferences = settingsFixture.observeUserPreferences,
        togglePreferredSortOrder = settingsFixture.togglePreferredSortOrder,
        updateSearchRadius = settingsFixture.updateSearchRadius,
        updateFuelType = settingsFixture.updateFuelType,
        updateBrandFilter = settingsFixture.updateBrandFilter,
        locationStateMachine = locationStateMachine,
        refreshCoordinator = refreshCoordinator,
        stationEventLogger = analytics,
        commandQueue = commandQueue,
    )
    runCurrent()
    return StationListViewModelFixture(
        viewModel = viewModel,
        locationStateMachine = locationStateMachine,
        refreshCoordinator = refreshCoordinator,
        searchOrchestrator = resolvedSearchOrchestrator,
        commandQueue = commandQueue,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.stationListViewModel(
    repository: StationRepository,
    settingsFixture: SettingsUseCaseTestFixture,
    locationRepository: LocationRepository,
    analytics: StationEventLogger = RecordingStationEventLogger(),
    searchOrchestrator: StationSearchOrchestrator? = null,
): StationListViewModel = stationListViewModelFixture(
    repository = repository,
    settingsFixture = settingsFixture,
    locationRepository = locationRepository,
    analytics = analytics,
    searchOrchestrator = searchOrchestrator,
).viewModel

internal fun stationEntry(
    id: String = "station-1",
    name: String = "강남주유소",
    priceDelta: StationPriceDelta = StationPriceDelta.Unavailable,
    isWatched: Boolean = false,
): StationListEntry = StationListEntry(
    station = Station(
        id = id,
        name = name,
        brand = Brand.GSC,
        price = com.gasstation.core.model.MoneyWon(1_689),
        distance = com.gasstation.core.model.DistanceMeters(800),
        coordinates = Coordinates(37.499095, 127.027610),
    ),
    priceDelta = priceDelta,
    isWatched = isWatched,
    lastSeenAt = Instant.parse("2026-04-18T00:00:00Z"),
)

internal fun emptySearchResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)

internal class FailingObservationStationRepository(private val failingSubscriptions: Set<Int> = setOf(1)) : StationRepository {
    var observationSubscriptions = 0
    var refreshCalls = 0

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flow {
        observationSubscriptions += 1
        if (observationSubscriptions in failingSubscriptions) {
            throw IllegalStateException("observation $observationSubscriptions failed")
        }
        emit(emptySearchResult())
        awaitCancellation()
    }

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = MutableSharedFlow()

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshCalls += 1
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult = WatchMutationResult.Committed

    override suspend fun removeWatchedStation(stationId: String): WatchMutationResult = WatchMutationResult.Committed
}
