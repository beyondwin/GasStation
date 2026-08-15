package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.MapProvider
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.feature.stationlist.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationListCommandIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = mainDispatcherRule.dispatcher

    @Test
    fun `refresh-state emission preserves pending command list and exact FIFO contents`() = runTest(dispatcher) {
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
            locationRepository = FakeLocationRepository(),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        repeat(2) {
            viewModel.onAction(StationListAction.RefreshRequested)
            advanceUntilIdle()
        }
        val commands = viewModel.uiState.value.pendingCommands

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        refreshStarted.await()
        runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertSame(commands, viewModel.uiState.value.pendingCommands)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.pendingCommands.map { it.id })
        assertEquals(
            listOf(
                StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_permission_denied)),
                StationListCommandPayload.ShowSnackbar(StringResource.fromId(R.string.station_list_permission_denied)),
            ),
            viewModel.uiState.value.pendingCommands.map { it.payload },
        )

        releaseRefresh.complete(Unit)
        advanceUntilIdle()
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
    fun `station click still queues external map command when analytics logging fails`() = runTest(dispatcher) {
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
}
