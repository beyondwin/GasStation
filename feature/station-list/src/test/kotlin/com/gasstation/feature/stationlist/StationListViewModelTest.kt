package com.gasstation.feature.stationlist

import app.cash.turbine.test
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.location.DemoLocationOverride
import com.gasstation.core.location.LocationLookupResult
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
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
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `refresh with precise location emits stale content state from repository`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(
                        stationEntry(
                            id = "station-1",
                            name = "강남주유소",
                            priceDelta = StationPriceDelta.Decreased(amountWon = 30),
                            isWatched = true,
                        ),
                    ),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            assertEquals(1, repository.refreshedQueries.size)
            assertEquals(SearchRadius.KM_3, repository.refreshedQueries.single().radius)
            assertEquals(FuelType.GASOLINE, repository.refreshedQueries.single().fuelType)
            assertEquals(BrandFilter.ALL, repository.refreshedQueries.single().brandFilter)
            assertEquals(SortOrder.DISTANCE, repository.refreshedQueries.single().sortOrder)
            assertEquals(MapProvider.TMAP, repository.refreshedQueries.single().mapProvider)
            assertEquals(LocationPermissionState.PreciseGranted, viewModel.uiState.value.permissionState)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertTrue(viewModel.uiState.value.isStale)
            assertEquals(1, viewModel.uiState.value.stations.size)
            assertEquals("30원", viewModel.uiState.value.stations.single().priceDeltaLabel)
            assertTrue(viewModel.uiState.value.stations.single().isWatched)
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
            val settingsRepository = FakeSettingsRepository(
                UserPreferences.default().copy(mapProvider = MapProvider.NAVER_MAP),
            )
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
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
    fun `title tap toggles persisted sort order`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(StationListAction.SortToggleRequested)
            advanceUntilIdle()

            assertEquals(SortOrder.PRICE, settingsRepository.current.sortOrder)
            assertEquals(SortOrder.PRICE, viewModel.uiState.value.selectedSortOrder)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ui state reflects persisted brand filter once preferences load`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(
                UserPreferences.default().copy(brandFilter = BrandFilter.SOL),
            )
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            advanceUntilIdle()

            assertEquals(BrandFilter.SOL, viewModel.uiState.value.selectedBrandFilter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh in demo mode uses override coordinates without remote refresh`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val demoCoordinates = Coordinates(37.497927, 127.027583)
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Unavailable,
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.of(DemoLocationOverride { demoCoordinates }),
            )

            viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = false))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            assertEquals(emptyList<StationQuery>(), repository.refreshedQueries)
            assertEquals(demoCoordinates, repository.observedQueries.last().coordinates)
            assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `demo mode treats denied permission as available for static seed`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val demoCoordinates = Coordinates(37.497927, 127.027583)
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Unavailable,
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.of(DemoLocationOverride { demoCoordinates }),
            )

            viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = false))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            assertEquals(LocationPermissionState.PreciseGranted, viewModel.uiState.value.permissionState)
            assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
            assertEquals(demoCoordinates, repository.observedQueries.last().coordinates)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `watch tap updates repository and emits analytics`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(
                        stationEntry(
                            id = "station-1",
                            isWatched = false,
                        ),
                    ),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            viewModel.onAction(StationListAction.WatchToggled("station-1", true))
            advanceUntilIdle()

            assertEquals(listOf("station-1" to true), repository.watchUpdates)
            assertEquals(
                listOf(StationEvent.WatchToggled(stationId = "station-1", watched = true)),
                analytics.events,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh failure emits snackbar and clears loading state`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
                refreshFailure = IllegalStateException("refresh failed"),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.effects.test {
                viewModel.onAction(
                    StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
                )
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("주유소 목록을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
            }
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(
                StationListFailureReason.RefreshFailed,
                viewModel.uiState.value.blockingFailure,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `location exception clears loading state and exposes blocking failure when cache is empty`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Error(IllegalStateException("gps crashed")),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.effects.test {
                viewModel.onAction(
                    StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
                )
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("현재 위치를 확인하지 못했습니다."),
                    awaitItem(),
                )
            }

            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(
                StationListFailureReason.LocationFailed,
                viewModel.uiState.value.blockingFailure,
            )
            assertTrue(viewModel.uiState.value.stations.isEmpty())
            assertTrue(repository.refreshedQueries.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `location timeout with no cache sets timed out blocking failure`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.TimedOut,
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.effects.test {
                viewModel.onAction(
                    StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
                )
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("현재 위치 확인이 지연되고 있습니다."),
                    awaitItem(),
                )
            }

            assertEquals(
                StationListFailureReason.LocationTimedOut,
                viewModel.uiState.value.blockingFailure,
            )
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `refresh timeout with no cache sets timed out blocking failure`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
                refreshFailure = StationRefreshException(StationRefreshFailureReason.Timeout),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.effects.test {
                viewModel.onAction(
                    StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
                )
                viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("서버 응답이 늦어 가격을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
            }

            assertEquals(
                StationListFailureReason.RefreshTimedOut,
                viewModel.uiState.value.blockingFailure,
            )
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cached snapshot with empty visible list keeps blocking failure null on refresh failure`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = Instant.parse("2026-04-18T01:00:00Z"),
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            assertEquals(emptyList<StationListItemUiModel>(), viewModel.uiState.value.stations)
            assertEquals(Instant.parse("2026-04-18T01:00:00Z"), viewModel.uiState.value.lastUpdatedAt)

            viewModel.effects.test {
                repository.refreshFailure = IllegalStateException("refresh failed")
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("주유소 목록을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
            }

            assertEquals(null, viewModel.uiState.value.blockingFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cached snapshot present with filtered empty list keeps timed out blocking failure null`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = Instant.parse("2026-04-18T01:00:00Z"),
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            viewModel.effects.test {
                repository.refreshFailure = StationRefreshException(StationRefreshFailureReason.Timeout)
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("서버 응답이 늦어 가격을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
            }

            assertEquals(null, viewModel.uiState.value.blockingFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `remote timeout with cached stations keeps list visible and only emits snackbar`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeStationRepository(
                result = StationSearchResult(
                    stations = listOf(stationEntry()),
                    freshness = StationFreshness.Stale,
                    fetchedAt = Instant.parse("2026-04-18T01:00:00Z"),
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val analytics = RecordingStationEventLogger()
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                updateWatchState = UpdateWatchStateUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
                ),
                stationEventLogger = analytics,
                demoLocationOverride = Optional.empty(),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.stations.size)
            assertEquals(null, viewModel.uiState.value.blockingFailure)

            viewModel.effects.test {
                repository.refreshFailure = StationRefreshException(StationRefreshFailureReason.Timeout)
                viewModel.onAction(StationListAction.RefreshRequested)
                advanceUntilIdle()

                assertEquals(
                    StationListEffect.ShowSnackbar("서버 응답이 늦어 가격을 새로고침하지 못했습니다."),
                    awaitItem(),
                )
            }

            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(1, viewModel.uiState.value.stations.size)
            assertEquals(null, viewModel.uiState.value.blockingFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeStationRepository(
    result: StationSearchResult,
    var refreshFailure: Throwable? = null,
) : StationRepository {
    private val state = MutableStateFlow(result)

    val refreshedQueries = mutableListOf<StationQuery>()
    val observedQueries = mutableListOf<StationQuery>()
    val watchUpdates = mutableListOf<Pair<String, Boolean>>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        observedQueries += query
        return state
    }

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> =
        MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshFailure?.let { throw it }
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) {
        watchUpdates += station.id to watched
    }
}

private class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    val current: UserPreferences
        get() = state.value

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}

private class FakeForegroundLocationProvider(
    private val result: LocationLookupResult,
) : ForegroundLocationProvider {
    override suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult =
        result
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
        price = MoneyWon(1_689),
        distance = DistanceMeters(800),
        coordinates = Coordinates(37.499095, 127.027610),
    ),
    priceDelta = priceDelta,
    isWatched = isWatched,
    lastSeenAt = Instant.parse("2026-04-18T00:00:00Z"),
)
