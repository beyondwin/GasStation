package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.feature.stationlist.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class StationListLocationIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = mainDispatcherRule.dispatcher

    @Test
    fun `refresh success exposes current address label when address lookup succeeds`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val coordinates = Coordinates(37.498095, 127.027610)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(coordinates),
                addressResult = LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32"),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals("서울 영등포구 당산동", viewModel.uiState.value.currentAddressLabel)
        assertEquals(coordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(1, viewModel.uiState.value.stations.size)
    }

    @Test
    fun `address lookup failure does not block station results`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                addressResult = LocationAddressLookupResult.Error(IllegalStateException("geocoder unavailable")),
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.currentAddressLabel)
        assertEquals(1, viewModel.uiState.value.stations.size)
        assertEquals(null, viewModel.uiState.value.blockingFailure)
    }

    @Test
    fun `new coordinates clear stale address before replacement address arrives`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val firstCoordinates = Coordinates(37.498095, 127.027610)
        val secondCoordinates = Coordinates(37.497927, 127.027583)
        val addressRequests = mutableListOf<Coordinates>()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                resultForPermission = {
                    if (repository.refreshedQueries.isEmpty()) {
                        LocationLookupResult.Success(firstCoordinates)
                    } else {
                        LocationLookupResult.Success(secondCoordinates)
                    }
                },
                addressResultForCoordinates = { coordinates ->
                    addressRequests += coordinates
                    if (coordinates == firstCoordinates) {
                        LocationAddressLookupResult.Success("서울 영등포구 당산동 194-32")
                    } else {
                        LocationAddressLookupResult.Unavailable
                    }
                },
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        assertEquals("서울 영등포구 당산동", viewModel.uiState.value.currentAddressLabel)

        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(secondCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(null, viewModel.uiState.value.currentAddressLabel)
        assertEquals(listOf(firstCoordinates, secondCoordinates), addressRequests)
    }

    @Test
    fun `collect location availability updates gps enabled state`() = runTest(dispatcher) {
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                availability = availability,
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        val collectionJob = launch { viewModel.collectLocationAvailability(availability) }
        availability.emit(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isGpsEnabled)

        availability.emit(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isGpsEnabled)

        collectionJob.cancel()
    }

    @Test
    fun `refresh success does not overwrite a newer gps off availability signal`() = runTest(dispatcher) {
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val locationLookupStarted = CompletableDeferred<Unit>()
        val completeLocationLookup = CompletableDeferred<Unit>()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = object : LocationRepository {
                override fun observeAvailability(): Flow<Boolean> = availability

                override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
                    locationLookupStarted.complete(Unit)
                    completeLocationLookup.await()
                    return LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
                }

                override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
                    LocationAddressLookupResult.Unavailable
            },
        )

        val collectionJob = launch { viewModel.collectLocationAvailability(availability) }
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        availability.emit(true)
        advanceUntilIdle()
        viewModel.onAction(StationListAction.RefreshRequested)
        locationLookupStarted.await()

        availability.emit(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)

        completeLocationLookup.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, repository.refreshedQueries.size)
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)
        collectionJob.cancel()
    }

    @Test
    fun `permission denial cancels remote refresh and finalizes state`() = runTest(dispatcher) {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val repository = FakeStationRepository(
            result = emptySearchResult(),
            refreshStarted = refreshStarted,
            releaseRefresh = releaseRefresh,
        )
        val fixture = stationListViewModelFixture(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )
        val viewModel = fixture.viewModel

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        refreshStarted.await()

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        assertEquals(RefreshCoordinatorState(), fixture.refreshCoordinator.state.value)
        runCurrent()
        releaseRefresh.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.refreshedQueries.size)
        assertTrue(repository.persistedRefreshQueries.isEmpty())
        assertEquals(null, viewModel.uiState.value.currentCoordinates)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `gps off cancels remote refresh and finalizes state`() = runTest(dispatcher) {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val repository = FakeStationRepository(
            result = emptySearchResult(),
            refreshStarted = refreshStarted,
            releaseRefresh = releaseRefresh,
        )
        val fixture = stationListViewModelFixture(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )
        val viewModel = fixture.viewModel

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        refreshStarted.await()

        viewModel.onAction(StationListAction.GpsAvailabilityChanged(false))
        assertEquals(RefreshCoordinatorState(), fixture.refreshCoordinator.state.value)
        runCurrent()
        releaseRefresh.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.refreshedQueries.size)
        assertTrue(repository.persistedRefreshQueries.isEmpty())
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `permission denial while location is pending drops late success before query`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val locationLookupStarted = CompletableDeferred<Unit>()
        val completeLocationLookup = CompletableDeferred<Unit>()
        val coordinates = Coordinates(37.498095, 127.027610)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = object : LocationRepository {
                override fun observeAvailability(): Flow<Boolean> = MutableSharedFlow()

                override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
                    locationLookupStarted.complete(Unit)
                    completeLocationLookup.await()
                    return LocationLookupResult.Success(coordinates)
                }

                override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
                    LocationAddressLookupResult.Success("서울 강남구 역삼동")
            },
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        locationLookupStarted.await()

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        completeLocationLookup.complete(Unit)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.currentCoordinates)
        assertEquals(null, viewModel.uiState.value.currentAddressLabel)
        assertTrue(repository.refreshedQueries.isEmpty())
        assertTrue(repository.observedQueries.isEmpty())
    }

    @Test
    fun `superseded location is silent with no analytics blocking failure or command`() = runTest(dispatcher, timeout = 10.seconds) {
        val repository = FakeStationRepository(emptySearchResult())
        val locationLookupStarted = CompletableDeferred<Unit>()
        val completeLocationLookup = CompletableDeferred<LocationLookupResult>()
        val analytics = RecordingStationEventLogger()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = object : LocationRepository {
                override fun observeAvailability(): Flow<Boolean> = MutableSharedFlow()

                override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
                    locationLookupStarted.complete(Unit)
                    return completeLocationLookup.await()
                }

                override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
                    LocationAddressLookupResult.Unavailable
            },
            analytics = analytics,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        locationLookupStarted.await()

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.ApproximateGranted))
        completeLocationLookup.complete(LocationLookupResult.Unavailable)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingCommands.isEmpty())

        assertTrue(analytics.events.isEmpty())
        assertEquals(null, viewModel.uiState.value.blockingFailure)
        assertTrue(repository.refreshedQueries.isEmpty())
    }

    @Test
    fun `denied auto refresh never asks location or station repository`() = runTest(dispatcher) {
        var locationRequests = 0
        val repository = FakeStationRepository(emptySearchResult())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                resultForPermission = {
                    locationRequests += 1
                    LocationLookupResult.Success(Coordinates(37.497927, 127.027583))
                },
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.AutoRefreshRequested)
        advanceUntilIdle()

        assertEquals(0, locationRequests)
        assertTrue(repository.refreshedQueries.isEmpty())
        assertEquals(StationListBodyState.PermissionRequired, viewModel.uiState.value.toBodyState())
    }

    @Test
    fun `permission denied location result emits snackbar without refresh`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.PermissionDenied,
            ),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_permission_denied)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )
        assertTrue(repository.refreshedQueries.isEmpty())
        assertEquals(null, viewModel.uiState.value.blockingFailure)
    }

    @Test
    fun `location acquisition failure logs location failed event`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val analytics = RecordingStationEventLogger()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Unavailable,
            ),
            analytics = analytics,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_location_failed)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )

        assertEquals(listOf(StationEvent.LocationFailed(resultType = "Unavailable")), analytics.events)
        assertTrue(repository.refreshedQueries.isEmpty())
    }

    @Test
    fun `manual refresh with gps disabled opens location settings when permission is granted`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(false))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.OpenLocationSettings,
            viewModel.uiState.value.pendingCommands.single().payload,
        )
        assertTrue(repository.refreshedQueries.isEmpty())
    }

    @Test
    fun `manual refresh with denied permission and gps disabled keeps permission guidance separate`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(false))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_permission_denied)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )
        assertTrue(repository.refreshedQueries.isEmpty())
    }

    @Test
    fun `location failure still shows snackbar and blocking failure when analytics logging fails`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Unavailable,
            ),
            analytics = ThrowingStationEventLogger(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_location_failed)),
            viewModel.uiState.value.pendingCommands.single().payload,
        )

        assertTrue(repository.refreshedQueries.isEmpty())
        assertEquals(StationListFailureReason.LocationFailed, viewModel.uiState.value.blockingFailure)
    }
}
