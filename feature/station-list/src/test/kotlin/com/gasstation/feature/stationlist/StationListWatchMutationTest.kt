package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationListWatchMutationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = mainDispatcherRule.dispatcher

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
}
