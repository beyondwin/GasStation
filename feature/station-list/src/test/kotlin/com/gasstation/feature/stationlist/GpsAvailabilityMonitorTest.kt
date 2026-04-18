package com.gasstation.feature.stationlist

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationLookupResult
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
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
import app.cash.turbine.test
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GpsAvailabilityMonitorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `provider change broadcast emits updated availability`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)

        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        context.gpsAvailabilityFlow().test {
            assertEquals(false, awaitItem())

            shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(true, awaitItem())

            shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `route ignores gps broadcasts while stopped and resumes updates in foreground`() {
        val context = composeRule.activity
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)
        val viewModel = stationListViewModelForRouteTest()

        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        composeRule.setContent {
            StationListRoute(
                onSettingsClick = {},
                onWatchlistClick = {},
                onOpenExternalMap = {},
                viewModel = viewModel,
            )
        }

        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)

        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertEquals(true, viewModel.uiState.value.isGpsEnabled)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()

        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertEquals(true, viewModel.uiState.value.isGpsEnabled)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertEquals(false, viewModel.uiState.value.isGpsEnabled)
    }
}

private fun stationListViewModelForRouteTest(): StationListViewModel {
    val repository = object : StationRepository {
        override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> =
            MutableStateFlow(
                StationSearchResult(
                    stations = emptyList(),
                    freshness = StationFreshness.Stale,
                    fetchedAt = null,
                ),
            )

        override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> =
            MutableStateFlow(emptyList())

        override suspend fun refreshNearbyStations(query: StationQuery) = Unit

        override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit
    }
    val settingsRepository = object : SettingsRepository {
        private val preferences = MutableStateFlow(UserPreferences.default())

        override fun observeUserPreferences(): Flow<UserPreferences> = preferences

        override suspend fun updateUserPreferences(
            transform: (UserPreferences) -> UserPreferences,
        ) {
            preferences.value = transform(preferences.value)
        }
    }

    return StationListViewModel(
        observeNearbyStations = ObserveNearbyStationsUseCase(repository),
        refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
        updateWatchState = UpdateWatchStateUseCase(repository),
        observeUserPreferences = ObserveUserPreferencesUseCase(settingsRepository),
        settingsRepository = settingsRepository,
        foregroundLocationProvider = object : ForegroundLocationProvider {
            override suspend fun currentLocation(
                permissionState: LocationPermissionState,
            ): LocationLookupResult = LocationLookupResult.Success(Coordinates(37.498095, 127.027610))
        },
        stationEventLogger = object : StationEventLogger {
            override fun log(event: com.gasstation.domain.station.model.StationEvent) = Unit
        },
        demoLocationOverride = Optional.empty(),
    )
}
