package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.feature.stationlist.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StationListRefreshIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = mainDispatcherRule.dispatcher

    @Test
    fun `view model publishes assembler output for collaborator snapshots`() = runTest(dispatcher) {
        val cachedAt = Instant.parse("2026-08-13T00:00:00Z")
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = cachedAt,
                hasCachedSnapshot = true,
            ),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertTrue(hasCachedSnapshot)
            assertTrue(isStale)
            assertEquals(cachedAt, lastUpdatedAt)
            assertEquals(UserPreferences.default(), preferences)
            assertEquals(StationListBodyState.Results, toBodyState())
        }
    }

    @Test
    fun `refresh with precise location builds query without map provider`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = true,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(1, repository.refreshedQueries.size)
        assertEquals(SearchRadius.KM_3, repository.refreshedQueries.single().radius)
        assertEquals(FuelType.GASOLINE, repository.refreshedQueries.single().fuelType)
        assertEquals(BrandFilter.ALL, repository.refreshedQueries.single().brandFilter)
        assertEquals(SortOrder.DISTANCE, repository.refreshedQueries.single().sortOrder)
        assertEquals(LocationPermissionState.PreciseGranted, viewModel.uiState.value.permissionState)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertTrue(viewModel.uiState.value.isStale)
        assertEquals(1, viewModel.uiState.value.stations.size)
    }

    @Test
    fun `freshness-only search emission preserves published station list identity`() = runTest(dispatcher) {
        val firstResult = StationSearchResult(
            stations = listOf(stationEntry()),
            freshness = StationFreshness.Fresh,
            fetchedAt = Instant.parse("2026-04-18T00:00:00Z"),
            hasCachedSnapshot = true,
        )
        val repository = FakeStationRepository(
            result = firstResult,
            useObservedResultsFlow = true,
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        val initialStations = viewModel.uiState.value.stations

        repository.emitObservedResult(
            firstResult.copy(
                freshness = StationFreshness.Stale,
                fetchedAt = Instant.parse("2026-04-18T00:01:00Z"),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isStale)
        assertTrue(viewModel.uiState.value.hasCachedSnapshot)
        assertEquals(Instant.parse("2026-04-18T00:01:00Z"), viewModel.uiState.value.lastUpdatedAt)
        assertSame(initialStations, viewModel.uiState.value.stations)
    }

    @Test
    fun `freshness update with equal station entries keeps mapped station item list instance`() = runTest(dispatcher) {
        val firstResult = StationSearchResult(
            stations = listOf(stationEntry()),
            freshness = StationFreshness.Fresh,
            fetchedAt = Instant.parse("2026-04-18T00:00:00Z"),
            hasCachedSnapshot = true,
        )
        val repository = FakeStationRepository(
            result = firstResult,
            useObservedResultsFlow = true,
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        val initialStations = viewModel.uiState.value.stations

        repository.emitObservedResult(
            firstResult.copy(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Stale,
                fetchedAt = Instant.parse("2026-04-18T00:01:00Z"),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isStale)
        assertEquals(Instant.parse("2026-04-18T00:01:00Z"), viewModel.uiState.value.lastUpdatedAt)
        assertSame(initialStations, viewModel.uiState.value.stations)
    }

    @Test
    fun `changing any station search criterion with a current location refreshes the new query`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val coordinates = Coordinates(37.498095, 127.027610)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(coordinates),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        settingsFixture.updatePreferences { current ->
            current.copy(searchRadius = SearchRadius.KM_5)
        }
        advanceUntilIdle()
        settingsFixture.updatePreferences { current ->
            current.copy(fuelType = FuelType.DIESEL)
        }
        advanceUntilIdle()
        settingsFixture.updatePreferences { current ->
            current.copy(brandFilter = BrandFilter.GSC)
        }
        advanceUntilIdle()
        settingsFixture.updatePreferences { current ->
            current.copy(sortOrder = SortOrder.PRICE)
        }
        advanceUntilIdle()

        assertEquals(
            listOf(
                UserPreferences.default(),
                UserPreferences.default().copy(searchRadius = SearchRadius.KM_5),
                UserPreferences.default().copy(
                    searchRadius = SearchRadius.KM_5,
                    fuelType = FuelType.DIESEL,
                ),
                UserPreferences.default().copy(
                    searchRadius = SearchRadius.KM_5,
                    fuelType = FuelType.DIESEL,
                    brandFilter = BrandFilter.GSC,
                ),
                UserPreferences.default().copy(
                    searchRadius = SearchRadius.KM_5,
                    fuelType = FuelType.DIESEL,
                    brandFilter = BrandFilter.GSC,
                    sortOrder = SortOrder.PRICE,
                ),
            ),
            repository.refreshedQueries.map { query ->
                UserPreferences.default().copy(
                    searchRadius = query.radius,
                    fuelType = query.fuelType,
                    brandFilter = query.brandFilter,
                    sortOrder = query.sortOrder,
                )
            },
        )
        assertTrue(repository.refreshedQueries.all { query -> query.coordinates == coordinates })
    }

    @Test
    fun `location completion refreshes only the latest preferences query`() = runTest(dispatcher) {
        val coordinates = Coordinates(37.498095, 127.027610)
        val initialPreferences = UserPreferences.default()
        val latestPreferences = initialPreferences.copy(
            searchRadius = SearchRadius.KM_5,
            fuelType = FuelType.DIESEL,
            brandFilter = BrandFilter.GSC,
            sortOrder = SortOrder.PRICE,
        )
        val settingsFixture = SettingsUseCaseTestFixture(initialPreferences)
        val repository = FakeStationRepository(emptySearchResult())
        val searchOrchestrator = StationSearchOrchestrator(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
        )
        val locationLookupStarted = CompletableDeferred<Unit>()
        val releaseLocationLookup = CompletableDeferred<Unit>()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = object : LocationRepository {
                override fun observeAvailability(): Flow<Boolean> = MutableSharedFlow()

                override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
                    locationLookupStarted.complete(Unit)
                    releaseLocationLookup.await()
                    return LocationLookupResult.Success(coordinates)
                }

                override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
                    LocationAddressLookupResult.Unavailable
            },
            searchOrchestrator = searchOrchestrator,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        locationLookupStarted.await()

        settingsFixture.emit(latestPreferences)
        runCurrent()
        releaseLocationLookup.complete(Unit)
        advanceUntilIdle()

        val expectedQuery = StationQuery(
            coordinates = coordinates,
            radius = latestPreferences.searchRadius,
            fuelType = latestPreferences.fuelType,
            brandFilter = latestPreferences.brandFilter,
            sortOrder = latestPreferences.sortOrder,
        )
        assertEquals(listOf(expectedQuery), repository.refreshedQueries)
        assertEquals(expectedQuery, searchOrchestrator.activeQueryState.value.query)
        assertEquals(latestPreferences, viewModel.uiState.value.preferences)
    }

    @Test
    fun `preference change replaces an active stale query refresh with the latest query`() = runTest(dispatcher) {
        val coordinates = Coordinates(37.498095, 127.027610)
        val initialPreferences = UserPreferences.default()
        val latestPreferences = initialPreferences.copy(fuelType = FuelType.DIESEL)
        val settingsFixture = SettingsUseCaseTestFixture(initialPreferences)
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val repository = FakeStationRepository(
            result = emptySearchResult(),
            refreshStarted = refreshStarted,
            releaseRefresh = releaseRefresh,
        )
        val searchOrchestrator = StationSearchOrchestrator(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(coordinates),
            ),
            searchOrchestrator = searchOrchestrator,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        refreshStarted.await()

        settingsFixture.emit(latestPreferences)
        runCurrent()
        releaseRefresh.complete(Unit)
        advanceUntilIdle()

        val initialQuery = StationQuery(
            coordinates = coordinates,
            radius = initialPreferences.searchRadius,
            fuelType = initialPreferences.fuelType,
            brandFilter = initialPreferences.brandFilter,
            sortOrder = initialPreferences.sortOrder,
        )
        val latestQuery = initialQuery.copy(fuelType = latestPreferences.fuelType)
        assertEquals(listOf(initialQuery, latestQuery), repository.refreshedQueries)
        assertEquals(listOf(latestQuery), repository.persistedRefreshQueries)
        assertEquals(latestQuery, searchOrchestrator.activeQueryState.value.query)
    }

    @Test
    fun `refresh failure queues durable snackbar without route collector and logs once`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val analytics = RecordingStationEventLogger()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
            analytics = analytics,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_refresh_failed)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )
        assertEquals(1, repository.refreshedQueries.size)
        assertEquals(StationListFailureReason.RefreshFailed, viewModel.uiState.value.blockingFailure)
        assertEquals(
            listOf(StationEvent.RefreshFailed(reason = StationRefreshFailureReason.Unknown)),
            analytics.events,
        )
    }

    @Test
    fun `refresh failure still shows snackbar and blocking failure when analytics logging fails`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
            analytics = ThrowingStationEventLogger(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_refresh_failed)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )

        assertEquals(1, repository.refreshedQueries.size)
        assertEquals(StationListFailureReason.RefreshFailed, viewModel.uiState.value.blockingFailure)
    }

    @Test
    fun `cached refresh timeout keeps stale stations visible and only emits snackbar`() = runTest(dispatcher) {
        val cachedAt = Instant.parse("2026-04-18T01:00:00Z")
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Stale,
                fetchedAt = cachedAt,
                hasCachedSnapshot = true,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        repository.refreshFailure = StationRefreshException(StationRefreshFailureReason.Timeout)

        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_refresh_timeout)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )

        assertEquals(2, repository.refreshedQueries.size)
        assertEquals(1, viewModel.uiState.value.stations.size)
        assertTrue(viewModel.uiState.value.isStale)
        assertEquals(cachedAt, viewModel.uiState.value.lastUpdatedAt)
        assertEquals(null, viewModel.uiState.value.blockingFailure)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `manual immediate refresh failure is associated with newly activated query`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
            useObservedResultsFlow = true,
            initialObservedResult = null,
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_refresh_failed)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )
        assertEquals(null, viewModel.uiState.value.blockingFailure)

        repository.emitObservedResult(
            StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.pendingCommands.size)

        assertEquals(1, repository.refreshedQueries.size)
        assertEquals(1, repository.observedQueries.size)
        assertEquals(repository.refreshedQueries.single(), repository.observedQueries.single())
        assertTrue(viewModel.uiState.value.stations.isEmpty())
        assertEquals(StationListFailureReason.RefreshFailed, viewModel.uiState.value.blockingFailure)
    }

    @Test
    fun `retry click retries failed observation without starting remote refresh`() = runTest(dispatcher) {
        val repository = FailingObservationStationRepository()
        val searchOrchestrator = StationSearchOrchestrator(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
            searchOrchestrator = searchOrchestrator,
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        assertTrue(searchOrchestrator.observationFailed.value)
        val refreshCallsBeforeRetry = repository.refreshCalls

        viewModel.onAction(StationListAction.RetryClicked)
        runCurrent()

        assertEquals(2, repository.observationSubscriptions)
        assertEquals(refreshCallsBeforeRetry, repository.refreshCalls)
        assertFalse(searchOrchestrator.observationFailed.value)
    }
}
