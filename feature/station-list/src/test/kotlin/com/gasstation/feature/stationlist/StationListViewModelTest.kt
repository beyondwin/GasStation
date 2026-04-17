package com.gasstation.feature.stationlist

import app.cash.turbine.test
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
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
                        station(
                            id = "station-1",
                            name = "강남주유소",
                        ),
                    ),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(UserPreferences.default())
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    Coordinates(37.498095, 127.027610),
                ),
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
                    stations = listOf(station()),
                    freshness = StationFreshness.Fresh,
                    fetchedAt = null,
                ),
            )
            val settingsRepository = FakeSettingsRepository(
                UserPreferences.default().copy(mapProvider = MapProvider.NAVER_MAP),
            )
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    Coordinates(37.498095, 127.027610),
                ),
            )

            viewModel.onAction(
                StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted),
            )
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isEnabled = true))
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onAction(
                    StationListAction.StationClicked(StationListItemUiModel(station())),
                )

                assertEquals(
                    StationListEffect.OpenExternalMap(
                        provider = MapProvider.NAVER_MAP,
                        stationName = "강남주유소",
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
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    Coordinates(37.498095, 127.027610),
                ),
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
            val viewModel = StationListViewModel(
                observeNearbyStations = ObserveNearbyStationsUseCase(repository),
                refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
                observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
                settingsRepository = settingsRepository,
                foregroundLocationProvider = FakeForegroundLocationProvider(
                    Coordinates(37.498095, 127.027610),
                ),
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
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeStationRepository(
    result: StationSearchResult,
    private val refreshFailure: Throwable? = null,
) : StationRepository {
    private val state = MutableStateFlow(result)

    val refreshedQueries = mutableListOf<StationQuery>()
    val observedQueries = mutableListOf<StationQuery>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        observedQueries += query
        return state
    }

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshFailure?.let { throw it }
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
    private val coordinates: Coordinates?,
) : ForegroundLocationProvider {
    override suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates? = coordinates
}

private fun station(
    id: String = "station-1",
    name: String = "강남주유소",
): Station = Station(
    id = id,
    name = name,
    brand = Brand.GSC,
    price = MoneyWon(1_689),
    distance = DistanceMeters(800),
    coordinates = Coordinates(37.499095, 127.027610),
)
