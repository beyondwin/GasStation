package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
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
import com.gasstation.feature.stationlist.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class StationListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = mainDispatcherRule.dispatcher

    @Test
    fun `preferences do not default or start a query before first emission`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val settings = SettingsUseCaseTestFixture(initialPreferences = null)
        val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.AutoRefreshRequested)
        viewModel.onAction(StationListAction.SortToggleRequested)
        viewModel.onAction(StationListAction.StationClicked(StationListItemUiModel(stationEntry())))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.preferences)
        assertTrue(repository.refreshedQueries.isEmpty())
        assertTrue(repository.observedQueries.isEmpty())
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `first persisted preferences create the first query without a default query`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val settings = SettingsUseCaseTestFixture(initialPreferences = null)
        val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())
        val expected = UserPreferences.default().copy(
            searchRadius = SearchRadius.KM_5,
            fuelType = FuelType.DIESEL,
            brandFilter = BrandFilter.GSC,
            sortOrder = SortOrder.PRICE,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        settings.emit(expected)
        runCurrent()
        viewModel.onAction(StationListAction.AutoRefreshRequested)
        advanceUntilIdle()

        assertEquals(expected, viewModel.uiState.value.preferences)
        assertEquals(SearchRadius.KM_5, repository.refreshedQueries.single().radius)
        assertEquals(FuelType.DIESEL, repository.refreshedQueries.single().fuelType)
        assertEquals(BrandFilter.GSC, repository.refreshedQueries.single().brandFilter)
        assertEquals(SortOrder.PRICE, repository.refreshedQueries.single().sortOrder)
    }

    @Test
    fun `preference read failure shows retryable failure without a default query`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val settings = SettingsUseCaseTestFixture(initialPreferences = null)
        val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        runCurrent()
        settings.fail(IllegalStateException("datastore read failed"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.preferenceLoadFailed)
        assertEquals(
            StationListBodyState.Failure(StationListFailureReason.PreferencesFailed),
            viewModel.uiState.value.toBodyState(),
        )
        assertTrue(repository.refreshedQueries.isEmpty())
    }

    @Test
    fun `retry after preference read failure activates only the newly persisted query`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val settings = SettingsUseCaseTestFixture()
        val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())
        val expected = UserPreferences.default().copy(
            searchRadius = SearchRadius.KM_5,
            fuelType = FuelType.DIESEL,
            brandFilter = BrandFilter.GSC,
            sortOrder = SortOrder.PRICE,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        repository.refreshedQueries.clear()
        repository.observedQueries.clear()

        settings.fail(IllegalStateException("datastore read failed"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preferenceLoadFailed)

        viewModel.onAction(StationListAction.RetryClicked)
        settings.emit(expected)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.preferenceLoadFailed)
        assertEquals(expected, viewModel.uiState.value.preferences)
        assertEquals(1, repository.observedQueries.size)
        assertTrue(repository.refreshedQueries.isEmpty())
        with(repository.observedQueries.single()) {
            assertEquals(SearchRadius.KM_5, radius)
            assertEquals(FuelType.DIESEL, fuelType)
            assertEquals(BrandFilter.GSC, brandFilter)
            assertEquals(SortOrder.PRICE, sortOrder)
            assertEquals(Coordinates(37.498095, 127.027610), coordinates)
        }
    }

    @Test
    fun `filter rail actions update preferences through domain use cases`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val settings = SettingsUseCaseTestFixture()
        val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

        viewModel.onAction(StationListAction.SearchRadiusSelected(SearchRadius.KM_5))
        advanceUntilIdle()
        viewModel.onAction(StationListAction.FuelTypeSelected(FuelType.DIESEL))
        advanceUntilIdle()
        viewModel.onAction(StationListAction.BrandFilterSelected(BrandFilter.ALTEUL))
        advanceUntilIdle()

        assertEquals(SearchRadius.KM_5, settings.currentPreferences.searchRadius)
        assertEquals(FuelType.DIESEL, settings.currentPreferences.fuelType)
        assertEquals(BrandFilter.ALTEUL, settings.currentPreferences.brandFilter)
    }

    @Test
    fun `preference writes admit only one immediate action while first is suspended`() = runTest(dispatcher) {
        val repository = FakeStationRepository(emptySearchResult())
        val settings = SettingsUseCaseTestFixture()
        val suspendedWrite = settings.suspendWrites()
        val viewModel = stationListViewModel(repository, settings, FakeLocationRepository())

        viewModel.onAction(StationListAction.SearchRadiusSelected(SearchRadius.KM_5))
        viewModel.onAction(StationListAction.FuelTypeSelected(FuelType.DIESEL))
        runCurrent()

        val callsBeforeRelease = settings.updateCallCount
        assertTrue(suspendedWrite.started.isCompleted)
        suspendedWrite.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, callsBeforeRelease)
        assertEquals(SearchRadius.KM_5, settings.currentPreferences.searchRadius)
        assertEquals(FuelType.GASOLINE, settings.currentPreferences.fuelType)
        assertFalse(viewModel.uiState.value.pendingPreferenceWrite)
    }

    @Test
    fun `refresh with precise location builds query without map provider`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
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
    fun `map provider preference update keeps mapped station item list instance`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = Instant.parse("2026-04-18T00:00:00Z"),
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

        val initialStations = viewModel.uiState.value.stations
        assertEquals(1, initialStations.size)

        settingsFixture.updatePreferences { it.copy(mapProvider = MapProvider.NAVER_MAP) }
        advanceUntilIdle()

        assertSame(initialStations, viewModel.uiState.value.stations)
    }

    @Test
    fun `freshness update with same station entries keeps mapped station item list instance`() = runTest(dispatcher) {
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
    fun `station click queues map command without collector and logs once`() = runTest(dispatcher) {
        val analytics = RecordingStationEventLogger()
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(
            UserPreferences.default().copy(mapProvider = MapProvider.NAVER_MAP),
        )
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

        viewModel.onAction(
            StationListAction.StationClicked(StationListItemUiModel(stationEntry())),
        )
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.OpenExternalMap(
                provider = MapProvider.NAVER_MAP,
                stationName = "강남주유소",
                originLatitude = 37.498095,
                originLongitude = 127.027610,
                latitude = 37.499095,
                longitude = 127.027610,
            ),
            viewModel.uiState.value.pendingCommands.single().payload,
        )
        val expectedEvents = listOf(
            StationEvent.ExternalMapOpened(
                stationId = "station-1",
                provider = MapProvider.NAVER_MAP,
            ),
        )
        assertEquals(expectedEvents, analytics.events)

        val command = viewModel.uiState.value.pendingCommands.single()
        repeat(2) {
            runCatching {
                handleAndAcknowledgeStationListCommand(
                    command = command,
                    handle = { throw IllegalStateException("route retry") },
                    acknowledge = { id -> viewModel.onAction(StationListAction.CommandHandled(id)) },
                )
            }
        }
        advanceUntilIdle()

        assertEquals(expectedEvents, analytics.events)
        assertEquals(listOf(command), viewModel.uiState.value.pendingCommands)

        handleAndAcknowledgeStationListCommand(
            command = command,
            handle = {},
            acknowledge = { id -> viewModel.onAction(StationListAction.CommandHandled(id)) },
        )
        advanceUntilIdle()

        assertEquals(expectedEvents, analytics.events)
        assertTrue(viewModel.uiState.value.pendingCommands.isEmpty())
    }

    @Test
    fun `two accepted feedback intents remain FIFO across a collector gap`() = runTest(dispatcher) {
        val viewModel = stationListViewModel(
            repository = FakeStationRepository(emptySearchResult()),
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
        )
        val expectedPayload = StationListCommandPayload.ShowSnackbar(
            StringResource.fromId(R.string.station_list_permission_denied),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        repeat(2) {
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()
        }

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.pendingCommands.map { it.id })
        assertEquals(
            listOf(expectedPayload, expectedPayload),
            viewModel.uiState.value.pendingCommands.map { it.payload },
        )
    }

    @Test
    fun `CommandHandled for tail cannot remove it and exact head acknowledgement advances`() = runTest(dispatcher) {
        val viewModel = stationListViewModel(
            repository = FakeStationRepository(emptySearchResult()),
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        repeat(2) {
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()
        }
        val initial = viewModel.uiState.value.pendingCommands

        viewModel.onAction(StationListAction.CommandHandled(initial.last().id))
        runCurrent()
        assertEquals(initial, viewModel.uiState.value.pendingCommands)

        viewModel.onAction(StationListAction.CommandHandled(initial.first().id))
        runCurrent()
        assertEquals(listOf(initial.last()), viewModel.uiState.value.pendingCommands)

        viewModel.onAction(StationListAction.CommandHandled(initial.first().id))
        runCurrent()
        assertEquals(listOf(initial.last()), viewModel.uiState.value.pendingCommands)
    }

    @Test
    fun `station click is ignored after permission denial despite prior coordinates`() = runTest(dispatcher) {
        val analytics = RecordingStationEventLogger()
        val repository = FakeStationRepository(emptySearchResult())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
            analytics = analytics,
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        analytics.events.clear()

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        viewModel.onAction(
            StationListAction.StationClicked(StationListItemUiModel(stationEntry())),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingCommands.isEmpty())
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `queued station click is cancelled when permission is denied before dispatch`() = runTest(dispatcher) {
        val analytics = RecordingStationEventLogger()
        val repository = FakeStationRepository(emptySearchResult())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
            analytics = analytics,
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        analytics.events.clear()

        viewModel.onAction(
            StationListAction.StationClicked(StationListItemUiModel(stationEntry())),
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        runCurrent()

        assertTrue(viewModel.uiState.value.pendingCommands.isEmpty())
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `station click still emits external map effect when analytics logging fails`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = false,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(
            UserPreferences.default().copy(mapProvider = MapProvider.NAVER_MAP),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
            analytics = ThrowingStationEventLogger(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        viewModel.onAction(
            StationListAction.StationClicked(StationListItemUiModel(stationEntry())),
        )
        advanceUntilIdle()

        assertEquals(
            StationListCommandPayload.OpenExternalMap(
                provider = MapProvider.NAVER_MAP,
                stationName = "강남주유소",
                originLatitude = 37.498095,
                originLongitude = 127.027610,
                latitude = 37.499095,
                longitude = 127.027610,
            ),
            viewModel.uiState.value.pendingCommands.single().payload,
        )
    }

    @Test
    fun `title tap toggles persisted sort order through use case`() = runTest(dispatcher) {
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
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )

        viewModel.onAction(StationListAction.SortToggleRequested)
        advanceUntilIdle()

        assertEquals(SortOrder.PRICE, settingsFixture.currentPreferences.sortOrder)
        assertEquals(SortOrder.PRICE, viewModel.uiState.value.preferences?.sortOrder)
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
    fun `permission denial cancels remote refresh and finalizes state`() = runTest(dispatcher) {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val repository = FakeStationRepository(
            result = emptySearchResult(),
            refreshStarted = refreshStarted,
            releaseRefresh = releaseRefresh,
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
        refreshStarted.await()

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
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
        refreshStarted.await()

        viewModel.onAction(StationListAction.GpsAvailabilityChanged(false))
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
    fun `watch toggle updates repository when analytics logging fails`() = runTest(dispatcher) {
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
            ),
            analytics = ThrowingStationEventLogger(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        viewModel.onAction(StationListAction.WatchToggled(stationId = "station-1", watched = true))
        advanceUntilIdle()

        assertEquals(listOf("station-1" to true), repository.watchStateUpdates)
    }

    @Test
    fun `committed watch toggle logs exactly one matching event`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = true,
            ),
        )
        val analytics = RecordingStationEventLogger()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
            analytics = analytics,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        analytics.events.clear()

        viewModel.onAction(StationListAction.WatchToggled(stationId = "station-1", watched = true))
        advanceUntilIdle()

        assertEquals(listOf("station-1" to true), repository.watchStateUpdates)
        assertEquals(listOf(StationEvent.WatchToggled("station-1", true)), analytics.events)
    }

    @Test
    fun `superseded watch toggle is silent`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = true,
            ),
            watchMutationResult = WatchMutationResult.Superseded,
        )
        val analytics = RecordingStationEventLogger()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
            analytics = analytics,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        analytics.events.clear()

        viewModel.onAction(StationListAction.WatchToggled(stationId = "station-1", watched = false))
        advanceUntilIdle()

        assertEquals(listOf("station-1" to false), repository.watchStateUpdates)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `cancelled watch toggle is not logged as success`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
                hasCachedSnapshot = true,
            ),
            watchMutationFailure = CancellationException("cancel watch"),
        )
        val analytics = RecordingStationEventLogger()
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
            analytics = analytics,
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        analytics.events.clear()

        viewModel.onAction(StationListAction.WatchToggled(stationId = "station-1", watched = true))
        advanceUntilIdle()

        assertTrue(analytics.events.isEmpty())
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

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.stationListViewModel(
    repository: StationRepository,
    settingsFixture: SettingsUseCaseTestFixture,
    locationRepository: LocationRepository,
    analytics: StationEventLogger = RecordingStationEventLogger(),
    searchOrchestrator: StationSearchOrchestrator? = null,
): StationListViewModel {
    val locationStateMachine = LocationStateMachine(
        getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
        getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
        observeAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    )
    val resolvedSearchOrchestrator = searchOrchestrator ?: StationSearchOrchestrator(
        observeNearbyStations = ObserveNearbyStationsUseCase(repository),
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
        refreshCoordinator = RefreshCoordinator(
            locationStateMachine = locationStateMachine,
            refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
        ),
        stationEventLogger = analytics,
        commandQueue = StationListCommandQueue(),
    )
    runCurrent()
    return viewModel
}

private fun stationEntry(
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

private fun emptySearchResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)

private class FailingObservationStationRepository : StationRepository {
    var observationSubscriptions = 0
    var refreshCalls = 0

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flow {
        observationSubscriptions += 1
        if (observationSubscriptions == 1) {
            throw IllegalStateException("first observation failed")
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
