package com.gasstation.feature.stationlist

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshCoordinatorTest {

    @Test
    fun `explicit denied request reports permission feedback without location or refresh call`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture()

        fixture.coordinator.request(
            scope = this,
            request = RefreshRequest.AcquireLocation(showPermissionDeniedFeedback = true),
            latestEligibleQuery = { null },
            onResult = fixture.results::add,
        )
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.PermissionRequired(showFeedback = true)), fixture.results)
        assertEquals(0, fixture.locationRepository.locationCalls)
        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `automatic denied request reports no-feedback permission result and remains idle`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture()

        fixture.coordinator.request(
            scope = this,
            request = RefreshRequest.AcquireLocation(showPermissionDeniedFeedback = false),
            latestEligibleQuery = { null },
            onResult = fixture.results::add,
        )
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.PermissionRequired(showFeedback = false)), fixture.results)
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `permission denial wins over gps disabled preflight`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture()
        fixture.locationStateMachine.onGpsAvailabilityChanged(false)

        fixture.requestAcquire(this, showFeedback = true)
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.PermissionRequired(showFeedback = true)), fixture.results)
        assertEquals(0, fixture.locationRepository.locationCalls)
    }

    @Test
    fun `gps disabled reports settings result without location or refresh call`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture()
        fixture.locationStateMachine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        fixture.locationStateMachine.onGpsAvailabilityChanged(false)

        fixture.requestAcquire(this, showFeedback = true)
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.GpsDisabled), fixture.results)
        assertEquals(0, fixture.locationRepository.locationCalls)
        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `superseded acquisition emits no result and finalizes indicators`() = runTest(timeout = 10.seconds) {
        val locationResult = CompletableDeferred<LocationLookupResult>()
        val fixture = coordinatorFixture(locationResult = { locationResult.await() }).apply { makeLocationUsable() }
        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()
        assertTrue(fixture.coordinator.state.value.isRefreshing)

        fixture.locationStateMachine.onPermissionChanged(LocationPermissionState.Denied)
        fixture.locationStateMachine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        locationResult.complete(LocationLookupResult.Success(COORDINATES))
        advanceUntilIdle()

        assertTrue(fixture.results.isEmpty())
        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `successful acquisition starts address resolution without delaying remote refresh`() = runTest(timeout = 10.seconds) {
        val addressStarted = CompletableDeferred<Unit>()
        val addressRelease = CompletableDeferred<Unit>()
        val fixture = coordinatorFixture(
            addressResult = {
                addressStarted.complete(Unit)
                addressRelease.await()
                LocationAddressLookupResult.Success("서울 강남구 역삼동 1")
            },
        ).apply {
            makeLocationUsable()
            latestQuery = QUERY
        }

        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()

        assertTrue(addressStarted.isCompleted)
        assertEquals(listOf(QUERY), fixture.stationRepository.refreshedQueries)
        assertEquals(
            listOf(
                RefreshCoordinatorResult.LocationAcquired(COORDINATES),
                RefreshCoordinatorResult.RefreshStarting(QUERY),
                RefreshCoordinatorResult.RefreshSucceeded(QUERY),
            ),
            fixture.results,
        )
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)

        addressRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals("서울 강남구 역삼동", fixture.locationStateMachine.state.value.currentAddressLabel)
    }

    @Test
    fun `superseded acquisition never starts address resolution`() = runTest(timeout = 10.seconds) {
        val locationResult = CompletableDeferred<LocationLookupResult>()
        val fixture = coordinatorFixture(
            locationResult = { locationResult.await() },
            addressResult = { LocationAddressLookupResult.Success("서울 강남구 역삼동 1") },
        ).apply { makeLocationUsable() }

        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()
        fixture.locationStateMachine.onPermissionChanged(LocationPermissionState.Denied)
        fixture.locationStateMachine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        locationResult.complete(LocationLookupResult.Success(COORDINATES))
        advanceUntilIdle()

        assertEquals(0, fixture.locationRepository.addressCalls)
        assertTrue(fixture.results.isEmpty())
        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
    }

    @Test
    fun `new location generation rejects late old address completion`() = runTest(timeout = 10.seconds) {
        val oldAddressStarted = CompletableDeferred<Unit>()
        val oldAddressRelease = CompletableDeferred<Unit>()
        var locationCall = 0
        val newCoordinates = Coordinates(37.510000, 127.040000)
        val newQuery = QUERY.copy(coordinates = newCoordinates)
        val fixture = coordinatorFixture(
            locationResult = {
                locationCall += 1
                LocationLookupResult.Success(if (locationCall == 1) COORDINATES else newCoordinates)
            },
            addressResult = { coordinates ->
                if (coordinates == COORDINATES) {
                    oldAddressStarted.complete(Unit)
                    oldAddressRelease.await()
                    LocationAddressLookupResult.Success("서울 강남구 오래된동 1")
                } else {
                    LocationAddressLookupResult.Unavailable
                }
            },
        ).apply {
            makeLocationUsable()
            latestQuery = QUERY
        }

        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()
        assertTrue(oldAddressStarted.isCompleted)

        fixture.latestQuery = newQuery
        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()
        assertEquals(newCoordinates, fixture.locationStateMachine.state.value.currentCoordinates)

        oldAddressRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(null, fixture.locationStateMachine.state.value.currentAddressLabel)
        assertEquals(listOf(QUERY, newQuery), fixture.stationRepository.refreshedQueries)
    }

    @Test
    fun `acquisition cancellation propagates no failure result and finalizes indicators`() = runTest(timeout = 10.seconds) {
        val locationResult = CompletableDeferred<LocationLookupResult>()
        val fixture = coordinatorFixture(locationResult = { locationResult.await() }).apply { makeLocationUsable() }
        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()

        fixture.coordinator.cancel()
        advanceUntilIdle()

        assertTrue(fixture.results.isEmpty())
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `remote cancellation propagates no failure result and finalizes indicators`() = runTest(timeout = 10.seconds) {
        val refreshRelease = CompletableDeferred<Unit>()
        val fixture = coordinatorFixture(refresh = { refreshRelease.await() }).apply { makeLocationUsable() }
        fixture.latestQuery = QUERY
        fixture.requestActive(this, QUERY)
        runCurrent()
        assertEquals(listOf(RefreshCoordinatorResult.RefreshStarting(QUERY)), fixture.results)

        fixture.coordinator.cancel()
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.RefreshStarting(QUERY)), fixture.results)
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `repository cancellation is not converted to refresh failure`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture(
            refresh = { throw CancellationException("repository cancelled") },
        ).apply {
            makeLocationUsable()
            latestQuery = QUERY
        }

        fixture.requestActive(this, QUERY)
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.RefreshStarting(QUERY)), fixture.results)
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `already cancelled supplied scope finalizes without entering work body`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture().apply {
            makeLocationUsable()
            latestQuery = QUERY
        }
        val owner = Job().apply { cancel() }
        val cancelledScope = CoroutineScope(owner + StandardTestDispatcher(testScheduler))

        fixture.requestActive(cancelledScope, QUERY)
        runCurrent()

        assertTrue(fixture.results.isEmpty())
        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `scope cancellation after start but before first dispatch finalizes without entering work body`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture().apply {
            makeLocationUsable()
            latestQuery = QUERY
        }
        val owner = Job()
        val queuedScope = CoroutineScope(owner + StandardTestDispatcher(testScheduler))

        fixture.requestActive(queuedScope, QUERY)
        assertEquals(
            RefreshCoordinatorState(isLoading = true, isRefreshing = true, activeQuery = QUERY),
            fixture.coordinator.state.value,
        )
        owner.cancel()
        runCurrent()

        assertTrue(fixture.results.isEmpty())
        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `new request cancels old work and old completion cannot clear new state`() = runTest(timeout = 10.seconds) {
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        val oldQuery = QUERY
        val newQuery = QUERY.copy(fuelType = FuelType.DIESEL)
        val fixture = coordinatorFixture(
            refresh = { query ->
                when (query) {
                    oldQuery -> {
                        oldStarted.complete(Unit)
                        try {
                            oldRelease.await()
                        } finally {
                            withContext(NonCancellable) { oldRelease.await() }
                        }
                    }

                    newQuery -> {
                        newStarted.complete(Unit)
                        newRelease.await()
                    }
                }
            },
        ).apply { makeLocationUsable() }

        fixture.latestQuery = oldQuery
        fixture.requestActive(this, oldQuery)
        oldStarted.await()
        fixture.latestQuery = newQuery
        fixture.requestActive(this, newQuery)
        newStarted.await()
        oldRelease.complete(Unit)
        runCurrent()

        assertEquals(
            RefreshCoordinatorState(isLoading = true, isRefreshing = true, activeQuery = newQuery),
            fixture.coordinator.state.value,
        )
        newRelease.complete(Unit)
        advanceUntilIdle()
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `delayed location completion refreshes only latest eligible preferences query`() = runTest(timeout = 10.seconds) {
        val locationResult = CompletableDeferred<LocationLookupResult>()
        val latestQuery = QUERY.copy(radius = SearchRadius.KM_5, fuelType = FuelType.DIESEL)
        val fixture = coordinatorFixture(locationResult = { locationResult.await() }).apply { makeLocationUsable() }
        fixture.latestQuery = QUERY
        fixture.requestAcquire(this, showFeedback = true)
        runCurrent()

        fixture.latestQuery = latestQuery
        locationResult.complete(LocationLookupResult.Success(COORDINATES))
        advanceUntilIdle()

        assertEquals(listOf(latestQuery), fixture.stationRepository.refreshedQueries)
        assertEquals(
            listOf(
                RefreshCoordinatorResult.LocationAcquired(COORDINATES),
                RefreshCoordinatorResult.RefreshStarting(latestQuery),
                RefreshCoordinatorResult.RefreshSucceeded(latestQuery),
            ),
            fixture.results,
        )
    }

    @Test
    fun `active query change suppresses late old success and failure delivery`() = runTest(timeout = 10.seconds) {
        val refreshRelease = CompletableDeferred<Unit>()
        val fixture = coordinatorFixture(refresh = { refreshRelease.await() }).apply { makeLocationUsable() }
        fixture.latestQuery = QUERY
        fixture.requestActive(this, QUERY)
        runCurrent()

        fixture.latestQuery = QUERY.copy(sortOrder = SortOrder.DISTANCE)
        refreshRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(RefreshCoordinatorResult.RefreshStarting(QUERY)), fixture.results)
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `same-coordinate criteria changes require refresh while different coordinates do not`() {
        val fixture = coordinatorFixture()
        val criteriaQuery = QUERY.copy(brandFilter = BrandFilter.GSC)
        val movedQuery = criteriaQuery.copy(coordinates = Coordinates(37.5, 127.03))

        assertTrue(fixture.coordinator.requiresRefresh(QUERY, criteriaQuery))
        assertFalse(fixture.coordinator.requiresRefresh(QUERY, movedQuery))
        assertFalse(fixture.coordinator.requiresRefresh(null, criteriaQuery))
        assertFalse(fixture.coordinator.requiresRefresh(QUERY, null))
    }

    @Test
    fun `ordinary refresh exception maps reason and callback order is starting then failed`() = runTest(timeout = 10.seconds) {
        val fixture = coordinatorFixture(
            refresh = { throw StationRefreshException(StationRefreshFailureReason.Timeout) },
        ).apply { makeLocationUsable() }
        fixture.latestQuery = QUERY

        fixture.requestActive(this, QUERY)
        advanceUntilIdle()

        assertEquals(
            listOf(
                RefreshCoordinatorResult.RefreshStarting(QUERY),
                RefreshCoordinatorResult.RefreshFailed(QUERY, StationRefreshFailureReason.Timeout),
            ),
            fixture.results,
        )
        assertEquals(RefreshCoordinatorState(), fixture.coordinator.state.value)
    }

    @Test
    fun `result callback completes inline before refresh work continues`() = runTest(timeout = 10.seconds) {
        val callbackStarted = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        val fixture = coordinatorFixture().apply {
            makeLocationUsable()
            latestQuery = QUERY
        }

        fixture.coordinator.request(
            scope = this,
            request = RefreshRequest.ActiveQuery(QUERY),
            latestEligibleQuery = { fixture.latestQuery },
            onResult = { result ->
                fixture.results += result
                if (result is RefreshCoordinatorResult.RefreshStarting) {
                    callbackStarted.complete(Unit)
                    releaseCallback.await()
                }
            },
        )
        callbackStarted.await()

        assertTrue(fixture.stationRepository.refreshedQueries.isEmpty())
        assertTrue(fixture.coordinator.state.value.isRefreshing)
        releaseCallback.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(QUERY), fixture.stationRepository.refreshedQueries)
        assertEquals(
            listOf(
                RefreshCoordinatorResult.RefreshStarting(QUERY),
                RefreshCoordinatorResult.RefreshSucceeded(QUERY),
            ),
            fixture.results,
        )
    }

    companion object {
        val COORDINATES = Coordinates(37.498095, 127.027610)
        val QUERY = StationQuery(
            coordinates = COORDINATES,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.PRICE,
        )
    }
}

private class RefreshCoordinatorFixture(
    val coordinator: RefreshCoordinator,
    val locationStateMachine: LocationStateMachine,
    val locationRepository: CoordinatorLocationRepository,
    val stationRepository: CoordinatorStationRepository,
) {
    val results = mutableListOf<RefreshCoordinatorResult>()
    var latestQuery: StationQuery? = null

    fun makeLocationUsable() {
        locationStateMachine.onPermissionChanged(LocationPermissionState.PreciseGranted)
        locationStateMachine.onGpsAvailabilityChanged(true)
    }

    fun requestAcquire(scope: kotlinx.coroutines.CoroutineScope, showFeedback: Boolean) {
        coordinator.request(
            scope = scope,
            request = RefreshRequest.AcquireLocation(showPermissionDeniedFeedback = showFeedback),
            latestEligibleQuery = { latestQuery },
            onResult = results::add,
        )
    }

    fun requestActive(scope: kotlinx.coroutines.CoroutineScope, query: StationQuery) {
        coordinator.request(
            scope = scope,
            request = RefreshRequest.ActiveQuery(query),
            latestEligibleQuery = { latestQuery },
            onResult = results::add,
        )
    }
}

private fun coordinatorFixture(
    locationResult: suspend (LocationPermissionState) -> LocationLookupResult = {
        LocationLookupResult.Success(RefreshCoordinatorTest.COORDINATES)
    },
    addressResult: suspend (Coordinates) -> LocationAddressLookupResult = {
        LocationAddressLookupResult.Unavailable
    },
    refresh: suspend (StationQuery) -> Unit = {},
): RefreshCoordinatorFixture {
    val locationRepository = CoordinatorLocationRepository(
        locationResult = locationResult,
        addressResult = addressResult,
    )
    val stationRepository = CoordinatorStationRepository(refresh)
    val locationStateMachine = LocationStateMachine(
        getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
        getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
        observeAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    )
    return RefreshCoordinatorFixture(
        coordinator = RefreshCoordinator(
            locationStateMachine = locationStateMachine,
            refreshNearbyStations = RefreshNearbyStationsUseCase(stationRepository),
        ),
        locationStateMachine = locationStateMachine,
        locationRepository = locationRepository,
        stationRepository = stationRepository,
    )
}

private class CoordinatorLocationRepository(
    private val locationResult: suspend (LocationPermissionState) -> LocationLookupResult,
    private val addressResult: suspend (Coordinates) -> LocationAddressLookupResult,
) : LocationRepository {
    var locationCalls: Int = 0
    var addressCalls: Int = 0

    override fun observeAvailability(): Flow<Boolean> = flowOf(true)

    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
        locationCalls += 1
        return locationResult(permissionState)
    }

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult {
        addressCalls += 1
        return addressResult(coordinates)
    }
}

private class CoordinatorStationRepository(private val refresh: suspend (StationQuery) -> Unit) : StationRepository {
    val refreshedQueries = mutableListOf<StationQuery>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = emptyFlow()

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = emptyFlow()

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refresh(query)
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult = WatchMutationResult.Committed

    override suspend fun removeWatchedStation(stationId: String): WatchMutationResult = WatchMutationResult.Committed
}
