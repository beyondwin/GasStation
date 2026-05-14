package com.gasstation.feature.stationlist

import androidx.activity.ComponentActivity
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
        updatePreferredSortOrder = settingsFixture.updatePreferredSortOrder,
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
        ),
    )

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> = MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) = Unit

    override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit
}
