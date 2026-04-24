package com.gasstation.feature.stationlist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GpsAvailabilityMonitorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `route ignores availability updates while stopped and resumes collection in foreground`() {
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        val fixture = stationListViewModelForRouteTest(availability)
        val viewModel = fixture.viewModel

        availability.tryEmit(false)

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)

        availability.tryEmit(true)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        assertEquals(true, viewModel.uiState.value.isGpsEnabled)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(composeRule.activity.mainLooper).idle()

        availability.tryEmit(false)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        assertEquals(true, viewModel.uiState.value.isGpsEnabled)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)
    }

    @Test
    fun `route auto refreshes immediately when demo style availability is already true`() {
        val demoCoordinates = Coordinates(37.497927, 127.027583)
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        availability.tryEmit(true)
        val fixture = stationListViewModelForRouteTest(
            availability = availability,
            resultForPermission = { permissionState ->
                if (permissionState == LocationPermissionState.Denied) {
                    LocationLookupResult.Success(demoCoordinates)
                } else {
                    LocationLookupResult.Unavailable
                }
            },
        )
        val viewModel = fixture.viewModel

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()

        assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(LocationPermissionState.Denied, viewModel.uiState.value.permissionState)
        assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)
        assertEquals(true, viewModel.uiState.value.isGpsEnabled)
        assertEquals(listOf(demoCoordinates), fixture.repository.refreshedQueries.map(StationQuery::coordinates))
        composeRule.onNodeWithContentDescription("북마크").assertExists()
    }

    @Test
    fun `route waits for first availability emission before auto refresh`() {
        val demoCoordinates = Coordinates(37.497927, 127.027583)
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        val fixture = stationListViewModelForRouteTest(
            availability = availability,
            resultForPermission = { permissionState ->
                if (permissionState == LocationPermissionState.Denied) {
                    LocationLookupResult.Success(demoCoordinates)
                } else {
                    LocationLookupResult.Unavailable
                }
            },
        )
        val viewModel = fixture.viewModel

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()

        assertEquals(emptyList<StationQuery>(), fixture.repository.refreshedQueries)
        assertEquals(null, viewModel.uiState.value.currentCoordinates)

        availability.tryEmit(true)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()

        assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(listOf(demoCoordinates), fixture.repository.refreshedQueries.map(StationQuery::coordinates))
    }

    @Test
    fun `route hides watchlist when denied permission only has stale prod coordinates`() {
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        availability.tryEmit(true)
        val fixture = stationListViewModelForRouteTest(availability)
        val viewModel = fixture.viewModel

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        shadowOf(composeRule.activity.mainLooper).idle()

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()

        assertEquals(LocationPermissionState.Denied, viewModel.uiState.value.permissionState)
        assertEquals(false, viewModel.uiState.value.hasDeniedLocationAccess)
        composeRule.onNodeWithContentDescription("북마크").assertDoesNotExist()
    }

    @Test
    fun `route auto refreshes again after permission recovery from denied demo coordinates`() {
        val demoCoordinates = Coordinates(37.497927, 127.027583)
        val realCoordinates = Coordinates(37.498095, 127.027610)
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        availability.tryEmit(true)
        val fixture = stationListViewModelForRouteTest(
            availability = availability,
            resultForPermission = { permissionState ->
                when (permissionState) {
                    LocationPermissionState.Denied -> LocationLookupResult.Success(demoCoordinates)
                    LocationPermissionState.PreciseGranted -> LocationLookupResult.Success(realCoordinates)
                    LocationPermissionState.ApproximateGranted -> LocationLookupResult.Unavailable
                }
            },
        )
        val viewModel = fixture.viewModel

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()
        assertEquals(demoCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(true, viewModel.uiState.value.hasDeniedLocationAccess)

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()

        assertEquals(realCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(false, viewModel.uiState.value.hasDeniedLocationAccess)
        assertEquals(
            listOf(demoCoordinates, realCoordinates),
            fixture.repository.refreshedQueries.map(StationQuery::coordinates),
        )
    }

    @Test
    fun `route auto refreshes again after approximate permission recovery from stale real coordinates`() {
        val initialCoordinates = Coordinates(37.498095, 127.027610)
        val recoveredCoordinates = Coordinates(37.499321, 127.028456)
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        availability.tryEmit(true)
        var preciseLookupCount = 0
        val fixture = stationListViewModelForRouteTest(
            availability = availability,
            resultForPermission = { permissionState ->
                when (permissionState) {
                    LocationPermissionState.PreciseGranted -> {
                        preciseLookupCount += 1
                        if (preciseLookupCount == 1) {
                            LocationLookupResult.Success(initialCoordinates)
                        } else {
                            LocationLookupResult.Success(recoveredCoordinates)
                        }
                    }

                    LocationPermissionState.ApproximateGranted ->
                        LocationLookupResult.Success(recoveredCoordinates)

                    LocationPermissionState.Denied -> LocationLookupResult.PermissionDenied
                }
            },
        )
        val viewModel = fixture.viewModel

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()
        assertEquals(initialCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(
            listOf(initialCoordinates),
            fixture.repository.refreshedQueries.map(StationQuery::coordinates),
        )

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.Denied))
        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()
        assertEquals(initialCoordinates, viewModel.uiState.value.currentCoordinates)

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.ApproximateGranted))
        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()

        assertEquals(recoveredCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(
            listOf(initialCoordinates, recoveredCoordinates),
            fixture.repository.refreshedQueries.map(StationQuery::coordinates),
        )
    }

    @Test
    fun `route auto refreshes again after gps recovery from stale real coordinates`() {
        val initialCoordinates = Coordinates(37.498095, 127.027610)
        val recoveredCoordinates = Coordinates(37.499321, 127.028456)
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        availability.tryEmit(true)
        var preciseLookupCount = 0
        val fixture = stationListViewModelForRouteTest(
            availability = availability,
            resultForPermission = { permissionState ->
                when (permissionState) {
                    LocationPermissionState.PreciseGranted -> {
                        preciseLookupCount += 1
                        if (preciseLookupCount == 1) {
                            LocationLookupResult.Success(initialCoordinates)
                        } else {
                            LocationLookupResult.Success(recoveredCoordinates)
                        }
                    }

                    LocationPermissionState.ApproximateGranted -> LocationLookupResult.Unavailable
                    LocationPermissionState.Denied -> LocationLookupResult.PermissionDenied
                }
            },
        )
        val viewModel = fixture.viewModel

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        composeRule.waitForIdle()
        shadowOf(composeRule.activity.mainLooper).idle()
        assertEquals(initialCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(
            listOf(initialCoordinates),
            fixture.repository.refreshedQueries.map(StationQuery::coordinates),
        )

        availability.tryEmit(false)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)
        assertEquals(initialCoordinates, viewModel.uiState.value.currentCoordinates)

        availability.tryEmit(true)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()

        assertEquals(recoveredCoordinates, viewModel.uiState.value.currentCoordinates)
        assertEquals(
            listOf(initialCoordinates, recoveredCoordinates),
            fixture.repository.refreshedQueries.map(StationQuery::coordinates),
        )
    }
}

private fun stationListViewModelForRouteTest(
    availability: MutableSharedFlow<Boolean>,
    resultForPermission: (LocationPermissionState) -> LocationLookupResult = {
        LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
    },
): RouteTestFixture {
    val repository = RecordingRouteStationRepository()
    val settingsFixture = SettingsUseCaseTestFixture()
    val locationRepository = object : LocationRepository {
        override fun observeAvailability(): Flow<Boolean> = availability

        override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult {
            return resultForPermission(permissionState)
        }

        override suspend fun getCurrentAddress(
            coordinates: Coordinates,
        ): LocationAddressLookupResult = LocationAddressLookupResult.Unavailable
    }

    val locationStateMachine = LocationStateMachine(
        getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
        getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
        observeAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    )

    return RouteTestFixture(
        viewModel = StationListViewModel(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
            refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
            updateWatchState = UpdateWatchStateUseCase(repository),
            observeUserPreferences = settingsFixture.observeUserPreferences,
            updatePreferredSortOrder = settingsFixture.updatePreferredSortOrder,
            locationStateMachine = locationStateMachine,
            stationEventLogger = object : StationEventLogger {
                override fun log(event: com.gasstation.domain.station.model.StationEvent) = Unit
            },
        ),
        repository = repository,
    )
}

private data class RouteTestFixture(
    val viewModel: StationListViewModel,
    val repository: RecordingRouteStationRepository,
)

private class RecordingRouteStationRepository : StationRepository {
    val refreshedQueries = mutableListOf<StationQuery>()

    override fun observeNearbyStations(query: StationQuery):
            Flow<StationSearchResult> = MutableStateFlow(
            StationSearchResult(
                stations = emptyList(),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
            ),
        )

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> =
        MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit
}
