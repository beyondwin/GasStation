package com.gasstation.feature.stationlist

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class StationListPreferencesTest {

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
    fun `retry preference failure wins over simultaneous observation failure`() = runTest(dispatcher) {
        val repository = FailingObservationStationRepository(failingSubscriptions = setOf(1, 2))
        val settings = SettingsUseCaseTestFixture(UserPreferences.default())
        val searchOrchestrator = StationSearchOrchestrator(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
        )
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settings,
            locationRepository = FakeLocationRepository(),
            searchOrchestrator = searchOrchestrator,
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        val activeQuery = checkNotNull(searchOrchestrator.activeQueryState.value.query)

        settings.fail(IllegalStateException("datastore read failed"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preferenceLoadFailed)

        val forcedObservation = launch {
            searchOrchestrator.observe(flowOf(activeQuery)).collect { }
        }
        advanceUntilIdle()
        assertTrue(searchOrchestrator.observationFailed.value)
        assertEquals(1, settings.observationSubscriptionCount)

        viewModel.onAction(StationListAction.RetryClicked)

        assertTrue(searchOrchestrator.observationFailed.value)
        runCurrent()
        assertEquals(2, settings.observationSubscriptionCount)
        forcedObservation.cancel()
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
}
