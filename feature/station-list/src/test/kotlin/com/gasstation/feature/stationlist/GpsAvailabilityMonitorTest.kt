package com.gasstation.feature.stationlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko")
class GpsAvailabilityMonitorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `route ignores availability updates while stopped and resumes collection in foreground`() {
        val availability = MutableSharedFlow<Boolean>(replay = 1)
        val viewModel = stationListViewModelForRouteTest(availability)

        availability.tryEmit(false)

        composeRule.setContent {
            StationListRoute(
                onCoordinatesAvailable = {},
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
    fun `route reports usable coordinates and clears them when policy becomes unavailable`() {
        val coordinates = Coordinates(37.498095, 127.027610)
        var uiState by mutableStateOf(
            StationListUiState(
                currentCoordinates = coordinates,
                permissionState = LocationPermissionState.PreciseGranted,
                isGpsEnabled = true,
            ),
        )
        val reportedCoordinates = mutableListOf<Coordinates?>()

        composeRule.setContent {
            StationListRouteCoordinatesEffect(
                uiState = uiState,
                onCoordinatesAvailable = { availableCoordinates: Coordinates? ->
                    reportedCoordinates.add(availableCoordinates)
                },
            )
        }

        composeRule.waitForIdle()
        assertEquals(coordinates, reportedCoordinates.last())

        composeRule.runOnUiThread {
            uiState = uiState.copy(isGpsEnabled = false)
        }
        composeRule.waitForIdle()
        assertEquals(null, reportedCoordinates.last())

        composeRule.runOnUiThread {
            uiState = uiState.copy(isGpsEnabled = true)
        }
        composeRule.waitForIdle()
        assertEquals(coordinates, reportedCoordinates.last())

        composeRule.runOnUiThread {
            uiState = uiState.copy(permissionState = LocationPermissionState.Denied)
        }
        composeRule.waitForIdle()
        assertEquals(null, reportedCoordinates.last())

        val distinctReports = reportedCoordinates.filterIndexed { index, value ->
            index == 0 || reportedCoordinates[index - 1] != value
        }
        assertEquals(listOf(coordinates, null, coordinates, null), distinctReports)
    }

    @Test
    fun `route re-evaluates stable coordinates when denied access recovery toggles`() {
        val coordinates = Coordinates(37.498095, 127.027610)
        var uiState by mutableStateOf(
            StationListUiState(
                currentCoordinates = coordinates,
                permissionState = LocationPermissionState.Denied,
                isGpsEnabled = true,
                hasDeniedLocationAccess = false,
            ),
        )
        val reportedCoordinates = mutableListOf<Coordinates?>()

        composeRule.setContent {
            StationListRouteCoordinatesEffect(
                uiState = uiState,
                onCoordinatesAvailable = reportedCoordinates::add,
            )
        }

        composeRule.waitForIdle()
        assertEquals(null, reportedCoordinates.last())

        composeRule.runOnUiThread {
            uiState = uiState.copy(hasDeniedLocationAccess = true)
        }
        composeRule.waitForIdle()
        assertEquals(coordinates, reportedCoordinates.last())

        composeRule.runOnUiThread {
            uiState = uiState.copy(hasDeniedLocationAccess = false)
        }
        composeRule.waitForIdle()
        assertEquals(null, reportedCoordinates.last())

        assertEquals(listOf(null, coordinates, null), reportedCoordinates)
    }
}

private fun stationListViewModelForRouteTest(
    availability: MutableSharedFlow<Boolean>,
    resultForPermission: (LocationPermissionState) -> LocationLookupResult = {
        LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
    },
): StationListViewModel {
    val repository = NoOpRouteStationRepository()
    val settingsFixture = SettingsUseCaseTestFixture()
    val locationRepository = object : LocationRepository {
        override fun observeAvailability(): Flow<Boolean> = availability

        override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult =
            resultForPermission(permissionState)

        override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult =
            LocationAddressLookupResult.Unavailable
    }

    val locationStateMachine = LocationStateMachine(
        getCurrentLocation = GetCurrentLocationUseCase(locationRepository),
        getCurrentAddress = GetCurrentAddressUseCase(locationRepository),
        observeAvailability = ObserveLocationAvailabilityUseCase(locationRepository),
    )

    return StationListViewModel(
        searchOrchestrator = StationSearchOrchestrator(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
            refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
        ),
        updateWatchState = UpdateWatchStateUseCase(repository),
        observeUserPreferences = settingsFixture.observeUserPreferences,
        togglePreferredSortOrder = settingsFixture.togglePreferredSortOrder,
        updateSearchRadius = settingsFixture.updateSearchRadius,
        updateFuelType = settingsFixture.updateFuelType,
        updateBrandFilter = settingsFixture.updateBrandFilter,
        locationStateMachine = locationStateMachine,
        stationEventLogger = object : StationEventLogger {
            override fun log(event: com.gasstation.domain.station.model.StationEvent) = Unit
        },
    )
}

private class NoOpRouteStationRepository : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = MutableStateFlow(
        StationSearchResult(
            stations = emptyList(),
            freshness = StationFreshness.Stale,
            fetchedAt = null,
            hasCachedSnapshot = false,
        ),
    )

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> = MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) = Unit

    override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit
}
