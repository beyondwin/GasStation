package com.gasstation.feature.stationlist

import app.cash.turbine.test
import com.gasstation.core.model.Coordinates
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
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `refresh with precise location builds query without map provider`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
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
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `changing any station search criterion with a current location refreshes the new query`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh success exposes current address label when address lookup succeeds`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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

            assertEquals("서울 영등포구 당산동 194-32", viewModel.uiState.value.currentAddressLabel)
            assertEquals(coordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(1, viewModel.uiState.value.stations.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `address lookup failure does not block station results`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `new coordinates clear stale address before replacement address arrives`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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
            assertEquals("서울 영등포구 당산동 194-32", viewModel.uiState.value.currentAddressLabel)

            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            assertEquals(secondCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(null, viewModel.uiState.value.currentAddressLabel)
            assertEquals(listOf(firstCoordinates, secondCoordinates), addressRequests)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `station click emits external map effect with persisted provider`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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
            )

            viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onAction(
                    StationListAction.StationClicked(StationListItemUiModel(stationEntry())),
                )

                assertEquals(
                    StationListEffect.OpenExternalMap(
                        provider = MapProvider.NAVER_MAP,
                        stationName = "강남주유소",
                        originLatitude = 37.498095,
                        originLongitude = 127.027610,
                        latitude = 37.499095,
                        longitude = 127.027610,
                    ),
                    awaitItem(),
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `title tap toggles persisted sort order through use case`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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
            assertEquals(SortOrder.PRICE, viewModel.uiState.value.selectedSortOrder)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `collect location availability updates gps enabled state`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val availability = MutableSharedFlow<Boolean>(replay = 1)
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh success does not overwrite a newer gps off availability signal`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val availability = MutableSharedFlow<Boolean>(replay = 1)
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
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

                    override suspend fun getCurrentLocation(
                        permissionState: LocationPermissionState,
                    ): LocationLookupResult {
                        locationLookupStarted.complete(Unit)
                        completeLocationLookup.await()
                        return LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
                    }

                    override suspend fun getCurrentAddress(
                        coordinates: Coordinates,
                    ): LocationAddressLookupResult = LocationAddressLookupResult.Unavailable
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

            assertEquals(1, repository.refreshedQueries.size)
            assertEquals(false, viewModel.uiState.value.isGpsEnabled)
            collectionJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `auto refresh with denied permission stays silent until a demo style location succeeds`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
            val demoCoordinates = Coordinates(37.497927, 127.027583)
            val viewModel = stationListViewModel(
                repository = repository,
                settingsFixture = settingsFixture,
                locationRepository = FakeLocationRepository(
                    resultForPermission = { permissionState ->
                        if (permissionState == LocationPermissionState.Denied) {
                            LocationLookupResult.Success(demoCoordinates)
                        } else {
                            LocationLookupResult.Unavailable
                        }
                    },
                ),
            )

            viewModel.effects.test {
                viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
                viewModel.onAction(StationListAction.AutoRefreshRequested)
                advanceUntilIdle()

                expectNoEvents()
            }
            advanceUntilIdle()

            assertEquals(LocationPermissionState.Denied, viewModel.uiState.value.permissionState)
            assertEquals(true, viewModel.uiState.value.isGpsEnabled)
            assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)
            assertEquals(demoCoordinates, repository.refreshedQueries.single().coordinates)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `permission recovery keeps denied access flag until granted refresh succeeds`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
            val demoCoordinates = Coordinates(37.497927, 127.027583)
            val realCoordinates = Coordinates(37.498095, 127.027610)
            val viewModel = stationListViewModel(
                repository = repository,
                settingsFixture = settingsFixture,
                locationRepository = FakeLocationRepository(
                    resultForPermission = { permissionState ->
                        when (permissionState) {
                            LocationPermissionState.Denied -> LocationLookupResult.Success(demoCoordinates)
                            LocationPermissionState.PreciseGranted -> LocationLookupResult.Success(realCoordinates)
                            LocationPermissionState.ApproximateGranted -> LocationLookupResult.Unavailable
                        }
                    },
                ),
            )

            viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
            viewModel.onAction(StationListAction.AutoRefreshRequested)
            advanceUntilIdle()

            assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)

            viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)

            viewModel.onAction(StationListAction.AutoRefreshRequested)
            advanceUntilIdle()

            assertEquals(realCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(false, viewModel.uiState.value.hasDeniedLocationAccess)
            assertEquals(
                listOf(demoCoordinates, realCoordinates),
                repository.refreshedQueries.map(StationQuery::coordinates),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `denied refresh failure after bypass keeps denied access active`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
            val demoCoordinates = Coordinates(37.497927, 127.027583)
            var deniedLookupCount = 0
            val viewModel = stationListViewModel(
                repository = repository,
                settingsFixture = settingsFixture,
                locationRepository = FakeLocationRepository(
                    resultForPermission = { permissionState ->
                        when (permissionState) {
                            LocationPermissionState.Denied -> {
                                deniedLookupCount += 1
                                if (deniedLookupCount == 1) {
                                    LocationLookupResult.Success(demoCoordinates)
                                } else {
                                    LocationLookupResult.PermissionDenied
                                }
                            }

                            LocationPermissionState.PreciseGranted,
                            LocationPermissionState.ApproximateGranted -> LocationLookupResult.Unavailable
                        }
                    },
                ),
            )

            viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
            viewModel.onAction(StationListAction.AutoRefreshRequested)
            advanceUntilIdle()

            assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)
            assertEquals(1, viewModel.uiState.value.stations.size)
            assertEquals(listOf(demoCoordinates), repository.refreshedQueries.map(StationQuery::coordinates))

            viewModel.effects.test {
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."),
                    awaitItem(),
                )
                expectNoEvents()
            }

            assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)
            assertEquals(1, viewModel.uiState.value.stations.size)
            assertEquals(listOf(demoCoordinates), repository.refreshedQueries.map(StationQuery::coordinates))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `permission denied location result emits snackbar without refresh`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
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

            viewModel.effects.test {
                viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."),
                    awaitItem(),
                )
            }
            assertTrue(repository.refreshedQueries.isEmpty())
            assertEquals(null, viewModel.uiState.value.blockingFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `manual refresh with gps disabled opens location settings when permission is granted`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )
            val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
            val viewModel = stationListViewModel(
                repository = repository,
                settingsFixture = settingsFixture,
                locationRepository = FakeLocationRepository(),
            )

            viewModel.effects.test {
                viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(false))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(StationListEffect.OpenLocationSettings, awaitItem())
                expectNoEvents()
            }
            assertTrue(repository.refreshedQueries.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `manual refresh with gps disabled and denied permission still opens location settings`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )
            val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
            val viewModel = stationListViewModel(
                repository = repository,
                settingsFixture = settingsFixture,
                locationRepository = FakeLocationRepository(),
            )

            viewModel.effects.test {
                viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(false))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(StationListEffect.OpenLocationSettings, awaitItem())
                expectNoEvents()
            }
            assertTrue(repository.refreshedQueries.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `auto refresh with denied permission and prod location denial stays silent without refresh`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
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

            viewModel.effects.test {
                viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
                viewModel.onAction(StationListAction.AutoRefreshRequested)
                advanceUntilIdle()

                expectNoEvents()
            }
            assertTrue(repository.refreshedQueries.isEmpty())
            assertEquals(null, viewModel.uiState.value.currentCoordinates)
            assertEquals(false, viewModel.uiState.value.hasDeniedLocationAccess)
            assertEquals(null, viewModel.uiState.value.blockingFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh failure without cache shows snackbar and blocking failure`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
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
            val viewModel = stationListViewModel(
                repository = repository,
                settingsFixture = settingsFixture,
                locationRepository = FakeLocationRepository(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
            )

            viewModel.effects.test {
                viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("주유소 목록을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
                expectNoEvents()
            }
            assertEquals(1, repository.refreshedQueries.size)
            assertEquals(StationListFailureReason.RefreshFailed, viewModel.uiState.value.blockingFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cached refresh timeout keeps stale stations visible and only emits snackbar`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val cachedAt = Instant.parse("2026-04-18T01:00:00Z")
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Stale,
                    fetchedAt = cachedAt,
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

            viewModel.effects.test {
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("서버 응답이 늦어 가격을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
                expectNoEvents()
            }

            assertEquals(2, repository.refreshedQueries.size)
            assertEquals(1, viewModel.uiState.value.stations.size)
            assertTrue(viewModel.uiState.value.isStale)
            assertEquals(cachedAt, viewModel.uiState.value.lastUpdatedAt)
            assertEquals(null, viewModel.uiState.value.blockingFailure)
            assertFalse(viewModel.uiState.value.isRefreshing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh failure promotes blocking failure after observed no-cache result resolves unknown cache state`() =
        runTest(dispatcher) {
            Dispatchers.setMain(dispatcher)
            try {
                val repository = FakeStationRepository(
                    result = StationSearchResult(
                        stations = emptyList(),
                        freshness = StationFreshness.Stale,
                        fetchedAt = null,
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

                viewModel.effects.test {
                    viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
                    viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
                    viewModel.onAction(StationListAction.RefreshRequested)
                    advanceUntilIdle()

                    assertEquals(
                        StationListEffect.ShowSnackbar("주유소 목록을 새로고침하지 못했습니다."),
                        awaitItem(),
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

                    expectNoEvents()
                }

                assertEquals(1, repository.refreshedQueries.size)
                assertEquals(1, repository.observedQueries.size)
                assertEquals(repository.refreshedQueries.single(), repository.observedQueries.single())
                assertTrue(viewModel.uiState.value.stations.isEmpty())
                assertEquals(StationListFailureReason.RefreshFailed, viewModel.uiState.value.blockingFailure)
            } finally {
                Dispatchers.resetMain()
            }
        }
}

private fun stationListViewModel(
    repository: StationRepository,
    settingsFixture: SettingsUseCaseTestFixture,
    locationRepository: LocationRepository,
    analytics: RecordingStationEventLogger = RecordingStationEventLogger(),
): StationListViewModel = StationListViewModel(
    observeNearbyStations = ObserveNearbyStationsUseCase(repository),
    refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
    updateWatchState = UpdateWatchStateUseCase(repository),
    observeUserPreferences = settingsFixture.observeUserPreferences,
    updatePreferredSortOrder = settingsFixture.updatePreferredSortOrder,
    observeLocationAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
    getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
    stationEventLogger = analytics,
)

private class FakeStationRepository(
    result: StationSearchResult,
    var refreshFailure: Throwable? = null,
    useObservedResultsFlow: Boolean = false,
    initialObservedResult: StationSearchResult? = result,
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
    val observedQueries = mutableListOf<StationQuery>()

    fun emitObservedResult(result: StationSearchResult) {
        checkNotNull(observedResults) { "Observed results flow is not enabled for this fake repository." }
            .tryEmit(result)
    }

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        observedQueries += query
        return observedResults ?: state
    }

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> =
        MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshFailure?.let { throw it }
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit
}

private class FakeLocationRepository(
    private val availability: Flow<Boolean> = MutableStateFlow(true),
    private val result: LocationLookupResult = LocationLookupResult.Success(
        Coordinates(37.498095, 127.027610),
    ),
    private val addressResult: LocationAddressLookupResult = LocationAddressLookupResult.Unavailable,
    private val resultForPermission: ((LocationPermissionState) -> LocationLookupResult)? = null,
    private val addressResultForCoordinates: ((Coordinates) -> LocationAddressLookupResult)? = null,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = availability

    override suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = resultForPermission?.invoke(permissionState) ?: result

    override suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): LocationAddressLookupResult = addressResultForCoordinates?.invoke(coordinates) ?: addressResult
}

private class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
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
