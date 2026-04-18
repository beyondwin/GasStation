package com.gasstation.feature.stationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.location.DemoLocationOverride
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationLookupResult
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
    private val updateWatchState: UpdateWatchStateUseCase,
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val settingsRepository: SettingsRepository,
    private val foregroundLocationProvider: ForegroundLocationProvider,
    private val stationEventLogger: StationEventLogger,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
) : ViewModel() {
    private val preferences = MutableStateFlow(UserPreferences.default())
    private val sessionState = MutableStateFlow(StationListSessionState())
    private val searchResult = MutableStateFlow(
        StationSearchResult(
            stations = emptyList(),
            freshness = StationFreshness.Stale,
            fetchedAt = null,
        ),
    )
    private val mutableUiState = MutableStateFlow(StationListUiState())
    private val mutableEffects = MutableSharedFlow<StationListEffect>()

    val uiState = mutableUiState.asStateFlow()
    val effects: SharedFlow<StationListEffect> = mutableEffects.asSharedFlow()

    init {
        observeUserPreferences()
            .onEach { preferences.value = it }
            .launchIn(viewModelScope)

        combine(preferences, sessionState) { prefs, session ->
            session.currentCoordinates?.takeIf {
                session.isGpsEnabled && session.permissionState != LocationPermissionState.Denied
            }?.let { coordinates -> buildQuery(preferences = prefs, coordinates = coordinates) }
        }.flatMapLatest { query ->
            if (query == null) {
                flowOf(
                    StationSearchResult(
                        stations = emptyList(),
                        freshness = StationFreshness.Stale,
                        fetchedAt = null,
                    ),
                )
            } else {
                observeNearbyStations(query)
            }
        }.onEach { searchResult.value = it }
            .launchIn(viewModelScope)

        combine(preferences, sessionState, searchResult) { prefs, session, result ->
            StationListUiState(
                currentCoordinates = session.currentCoordinates,
                permissionState = session.permissionState,
                isGpsEnabled = session.isGpsEnabled,
                isLoading = session.isLoading,
                isRefreshing = session.isRefreshing,
                isStale = result.freshness is StationFreshness.Stale,
                blockingFailure = session.blockingFailure,
                stations = result.stations.map(::StationListItemUiModel),
                selectedBrandFilter = prefs.brandFilter,
                selectedRadius = prefs.searchRadius,
                selectedFuelType = prefs.fuelType,
                selectedSortOrder = prefs.sortOrder,
                lastUpdatedAt = result.fetchedAt,
            )
        }.onEach { mutableUiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onAction(action: StationListAction) {
        when (action) {
            StationListAction.RefreshRequested,
            StationListAction.RetryClicked -> refresh()

            StationListAction.SortToggleRequested -> toggleSortOrder()

            is StationListAction.WatchToggled -> toggleWatchState(
                stationId = action.stationId,
                watched = action.watched,
            )

            is StationListAction.PermissionChanged -> sessionState.update {
                it.copy(permissionState = effectivePermissionState(action.permissionState))
            }

            is StationListAction.GpsAvailabilityChanged -> sessionState.update {
                it.copy(isGpsEnabled = action.isEnabled || demoLocationOverride.isPresent)
            }

            is StationListAction.StationClicked -> viewModelScope.launch {
                val currentCoordinates = sessionState.value.currentCoordinates
                mutableEffects.emit(
                    StationListEffect.OpenExternalMap(
                        provider = preferences.value.mapProvider,
                        stationName = action.station.name,
                        originLatitude = currentCoordinates?.latitude,
                        originLongitude = currentCoordinates?.longitude,
                        latitude = action.station.latitude,
                        longitude = action.station.longitude,
                    ),
                )
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val session = sessionState.value
            if (!session.isGpsEnabled) {
                mutableEffects.emit(StationListEffect.OpenLocationSettings)
                return@launch
            }
            if (effectivePermissionState(session.permissionState) == LocationPermissionState.Denied) {
                mutableEffects.emit(StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."))
                return@launch
            }

            sessionState.update {
                it.copy(
                    isLoading = it.currentCoordinates == null,
                    isRefreshing = true,
                )
            }

            try {
                val demoCoordinates = demoLocationOverride
                    .takeIf(Optional<DemoLocationOverride>::isPresent)
                    ?.get()
                    ?.currentLocation(session.permissionState)
                if (demoCoordinates != null) {
                    sessionState.update {
                        it.copy(
                            currentCoordinates = demoCoordinates,
                            blockingFailure = null,
                        )
                    }
                    return@launch
                }

                val coordinates = handleLocationResult(
                    foregroundLocationProvider.currentLocation(session.permissionState),
                ) ?: return@launch

                sessionState.update {
                    it.copy(
                        currentCoordinates = coordinates,
                        blockingFailure = null,
                    )
                }

                runCatching {
                    refreshNearbyStations(buildQuery(preferences.value, coordinates))
                }.onSuccess {
                    sessionState.update { current -> current.copy(blockingFailure = null) }
                }.onFailure { throwable ->
                    handleRefreshFailure((throwable as? StationRefreshException)?.reason)
                }
            } finally {
                sessionState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    private suspend fun handleLocationResult(
        result: LocationLookupResult,
    ): Coordinates? = when (result) {
        is LocationLookupResult.Success -> {
            sessionState.update { it.copy(blockingFailure = null) }
            result.coordinates
        }

        LocationLookupResult.TimedOut -> {
            onBlockingFailure(
                reason = StationListFailureReason.LocationTimedOut,
                message = "현재 위치 확인이 지연되고 있습니다.",
            )
            null
        }

        LocationLookupResult.Unavailable,
        LocationLookupResult.PermissionDenied,
        is LocationLookupResult.Error -> {
            onBlockingFailure(
                reason = StationListFailureReason.LocationFailed,
                message = "현재 위치를 확인하지 못했습니다.",
            )
            null
        }
    }

    private suspend fun handleRefreshFailure(
        reason: StationRefreshFailureReason?,
    ) {
        when (reason) {
            StationRefreshFailureReason.Timeout -> onBlockingFailure(
                reason = StationListFailureReason.RefreshTimedOut,
                message = "서버 응답이 늦어 가격을 새로고침하지 못했습니다.",
            )

            StationRefreshFailureReason.Network,
            StationRefreshFailureReason.InvalidPayload,
            StationRefreshFailureReason.Unknown,
            null -> onBlockingFailure(
                reason = StationListFailureReason.RefreshFailed,
                message = "주유소 목록을 새로고침하지 못했습니다.",
            )
        }
    }

    private suspend fun onBlockingFailure(
        reason: StationListFailureReason,
        message: String,
    ) {
        val hasCachedSnapshot = searchResult.value.fetchedAt != null
        sessionState.update {
            it.copy(blockingFailure = if (hasCachedSnapshot) null else reason)
        }
        mutableEffects.emit(StationListEffect.ShowSnackbar(message))
    }

    private fun toggleSortOrder() {
        viewModelScope.launch {
            val toggled = when (preferences.value.sortOrder) {
                SortOrder.DISTANCE -> SortOrder.PRICE
                SortOrder.PRICE -> SortOrder.DISTANCE
            }
            settingsRepository.updateUserPreferences { current ->
                current.copy(sortOrder = toggled)
            }
        }
    }

    private fun toggleWatchState(
        stationId: String,
        watched: Boolean,
    ) {
        viewModelScope.launch {
            val entry = searchResult.value.stations.firstOrNull { it.station.id == stationId } ?: return@launch
            updateWatchState(entry.station, watched)
            stationEventLogger.log(
                StationEvent.WatchToggled(
                    stationId = stationId,
                    watched = watched,
                ),
            )
        }
    }

    private fun buildQuery(
        preferences: UserPreferences,
        coordinates: Coordinates,
    ): StationQuery = StationQuery(
        coordinates = coordinates,
        radius = preferences.searchRadius,
        fuelType = preferences.fuelType,
        brandFilter = preferences.brandFilter,
        sortOrder = preferences.sortOrder,
        mapProvider = preferences.mapProvider,
    )

    private fun effectivePermissionState(
        permissionState: LocationPermissionState,
    ): LocationPermissionState = if (demoLocationOverride.isPresent) {
        LocationPermissionState.PreciseGranted
    } else {
        permissionState
    }
}

private data class StationListSessionState(
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val isGpsEnabled: Boolean = true,
    val currentCoordinates: Coordinates? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
)
