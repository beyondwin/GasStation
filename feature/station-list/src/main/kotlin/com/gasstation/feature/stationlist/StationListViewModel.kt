package com.gasstation.feature.stationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.GetCurrentAddressUseCase
import com.gasstation.domain.location.GetCurrentLocationUseCase
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.ObserveLocationAvailabilityUseCase
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val observeLocationAvailability: ObserveLocationAvailabilityUseCase,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val getCurrentAddress: GetCurrentAddressUseCase,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val preferences = MutableStateFlow(UserPreferences.default())
    private val sessionState = MutableStateFlow(StationListSessionState())
    private val activeQueryState = MutableStateFlow(ActiveStationQueryState())
    private val pendingBlockingFailure = MutableStateFlow<PendingBlockingFailure?>(null)
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
                session.isGpsEnabled &&
                    (
                        session.permissionState != LocationPermissionState.Denied ||
                            session.hasDeniedLocationAccess
                        )
            }?.let { coordinates -> buildQuery(preferences = prefs, coordinates = coordinates) }
        }.distinctUntilChanged()
            .onEach { query ->
                val previousQuery = activeQueryState.value.query
                activeQueryState.value = ActiveStationQueryState(
                    query = query,
                    cacheState = if (query == null) CachedSnapshotState.Absent else CachedSnapshotState.Unknown,
                )
                if (previousQuery != query) {
                    pendingBlockingFailure.value = null
                    sessionState.update { it.copy(blockingFailure = null) }
                }
            }
            .flatMapLatest { query ->
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
            .onEach { result ->
                val hasCachedSnapshot = result.hasCachedSnapshot
                activeQueryState.update { current ->
                    current.copy(
                        cacheState = if (hasCachedSnapshot) {
                            CachedSnapshotState.Present
                        } else {
                            CachedSnapshotState.Absent
                        },
                    )
                }
                syncBlockingFailureWithObservedResult(hasCachedSnapshot)
            }
            .launchIn(viewModelScope)

        combine(preferences, sessionState, searchResult) { prefs, session, result ->
            StationListUiState(
                currentCoordinates = session.currentCoordinates,
                currentAddressLabel = session.currentAddressLabel,
                permissionState = session.permissionState,
                hasDeniedLocationAccess = session.hasDeniedLocationAccess,
                needsRecoveryRefresh = session.needsRecoveryRefresh,
                isGpsEnabled = session.isGpsEnabled,
                isAvailabilityKnown = session.isAvailabilityKnown,
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
            StationListAction.AutoRefreshRequested -> refresh(
                showPermissionDeniedFeedback = false,
            )

            StationListAction.RefreshRequested,
            StationListAction.RetryClicked -> refresh(
                showPermissionDeniedFeedback = true,
            )

            StationListAction.SortToggleRequested -> toggleSortOrder()

            is StationListAction.WatchToggled -> toggleWatchState(
                stationId = action.stationId,
                watched = action.watched,
            )

            is StationListAction.PermissionChanged -> sessionState.update {
                it.withLocationRecoveryState(permissionState = action.permissionState)
            }

            is StationListAction.GpsAvailabilityChanged -> sessionState.update {
                it.withLocationRecoveryState(
                    isGpsEnabled = action.isEnabled,
                    isAvailabilityKnown = true,
                )
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

    suspend fun collectLocationAvailability(
        flowOverride: Flow<Boolean>? = null,
    ) {
        (flowOverride ?: observeLocationAvailability())
            .collect { isEnabled ->
                onAction(StationListAction.GpsAvailabilityChanged(isEnabled))
            }
    }

    private fun refresh(
        showPermissionDeniedFeedback: Boolean,
    ) {
        viewModelScope.launch {
            val session = sessionState.value
            if (!session.isGpsEnabled) {
                mutableEffects.emit(StationListEffect.OpenLocationSettings)
                return@launch
            }

            sessionState.update {
                it.copy(
                    isLoading = it.currentCoordinates == null,
                    isRefreshing = true,
                )
            }

            try {
                val coordinates = handleLocationResult(
                    getCurrentLocation(session.permissionState),
                    showPermissionDeniedFeedback = showPermissionDeniedFeedback,
                ) ?: return@launch

                val previousCoordinates = sessionState.value.currentCoordinates
                sessionState.update {
                    it.copy(
                        currentCoordinates = coordinates,
                        currentAddressLabel = if (previousCoordinates == coordinates) {
                            it.currentAddressLabel
                        } else {
                            null
                        },
                        hasDeniedLocationAccess = session.permissionState == LocationPermissionState.Denied,
                        needsRecoveryRefresh = false,
                        blockingFailure = null,
                    )
                }
                refreshAddressLabel(coordinates)

                val query = buildQuery(preferences.value, coordinates)
                activeQueryState.update { current ->
                    if (current.query == query) current else ActiveStationQueryState(
                        query = query,
                        cacheState = CachedSnapshotState.Unknown,
                    )
                }

                try {
                    refreshNearbyStations(query)
                    if (activeQueryState.value.query == query) {
                        pendingBlockingFailure.value = null
                        sessionState.update { current -> current.copy(blockingFailure = null) }
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (throwable: Throwable) {
                    handleRefreshFailure(query, (throwable as? StationRefreshException)?.reason)
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

    private fun refreshAddressLabel(coordinates: Coordinates) {
        viewModelScope.launch {
            val addressLabel = when (val result = getCurrentAddress(coordinates)) {
                is LocationAddressLookupResult.Success -> result.addressLabel
                LocationAddressLookupResult.Unavailable,
                is LocationAddressLookupResult.Error -> null
            }

            sessionState.update { current ->
                if (current.currentCoordinates == coordinates) {
                    current.copy(currentAddressLabel = addressLabel)
                } else {
                    current
                }
            }
        }
    }

    private suspend fun handleLocationResult(
        result: LocationLookupResult,
        showPermissionDeniedFeedback: Boolean,
    ): Coordinates? = when (result) {
        is LocationLookupResult.Success -> {
            sessionState.update { it.copy(blockingFailure = null) }
            result.coordinates
        }

        LocationLookupResult.PermissionDenied -> {
            if (showPermissionDeniedFeedback) {
                mutableEffects.emit(StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."))
            }
            null
        }

        LocationLookupResult.TimedOut -> {
            onBlockingFailure(
                reason = StationListFailureReason.LocationTimedOut,
                message = "현재 위치 확인이 지연되고 있습니다.",
            )
            null
        }

        LocationLookupResult.Unavailable,
        is LocationLookupResult.Error -> {
            onBlockingFailure(
                reason = StationListFailureReason.LocationFailed,
                message = "현재 위치를 확인하지 못했습니다.",
            )
            null
        }
    }

    private suspend fun handleRefreshFailure(
        query: StationQuery,
        reason: StationRefreshFailureReason?,
    ) {
        if (activeQueryState.value.query != query) return

        when (reason) {
            StationRefreshFailureReason.Timeout -> onBlockingFailure(
                query = query,
                reason = StationListFailureReason.RefreshTimedOut,
                message = "서버 응답이 늦어 가격을 새로고침하지 못했습니다.",
            )

            StationRefreshFailureReason.Network,
            StationRefreshFailureReason.InvalidPayload,
            StationRefreshFailureReason.Unknown,
            null -> onBlockingFailure(
                query = query,
                reason = StationListFailureReason.RefreshFailed,
                message = "주유소 목록을 새로고침하지 못했습니다.",
            )
        }
    }

    private suspend fun onBlockingFailure(
        query: StationQuery? = activeQueryState.value.query,
        reason: StationListFailureReason,
        message: String,
    ) {
        when (activeQueryState.value.cacheState) {
            CachedSnapshotState.Present -> {
                pendingBlockingFailure.value = null
                sessionState.update { it.copy(blockingFailure = null) }
            }

            CachedSnapshotState.Absent -> {
                pendingBlockingFailure.value = null
                sessionState.update { it.copy(blockingFailure = reason) }
            }

            CachedSnapshotState.Unknown -> {
                pendingBlockingFailure.value = query?.let { PendingBlockingFailure(it, reason) }
            }
        }
        mutableEffects.emit(StationListEffect.ShowSnackbar(message))
    }

    private fun syncBlockingFailureWithObservedResult(hasCachedSnapshot: Boolean) {
        if (hasCachedSnapshot) {
            pendingBlockingFailure.value = null
            sessionState.update { it.copy(blockingFailure = null) }
            return
        }

        val activeQuery = activeQueryState.value.query ?: return
        val pendingFailure = pendingBlockingFailure.value
            ?.takeIf { it.query == activeQuery }
            ?: return
        pendingBlockingFailure.value = null
        sessionState.update { it.copy(blockingFailure = pendingFailure.reason) }
    }

    private fun toggleSortOrder() {
        viewModelScope.launch {
            val toggled = when (preferences.value.sortOrder) {
                SortOrder.DISTANCE -> SortOrder.PRICE
                SortOrder.PRICE -> SortOrder.DISTANCE
            }
            updatePreferredSortOrder(toggled)
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
    )
}

private data class StationListSessionState(
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val hasDeniedLocationAccess: Boolean = false,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
)

private fun StationListSessionState.withLocationRecoveryState(
    permissionState: LocationPermissionState = this.permissionState,
    isGpsEnabled: Boolean = this.isGpsEnabled,
    isAvailabilityKnown: Boolean = this.isAvailabilityKnown,
): StationListSessionState {
    val updated = copy(
        permissionState = permissionState,
        isGpsEnabled = isGpsEnabled,
        isAvailabilityKnown = isAvailabilityKnown,
    )
    val needsRecoveryRefresh = !isLocationUsable() &&
        updated.isLocationUsable() &&
        currentCoordinates != null &&
        !hasDeniedLocationAccess
    return updated.copy(
        needsRecoveryRefresh = updated.needsRecoveryRefresh || needsRecoveryRefresh,
    )
}

private fun StationListSessionState.isLocationUsable(): Boolean =
    isGpsEnabled &&
        (
            permissionState != LocationPermissionState.Denied ||
                hasDeniedLocationAccess
            )

private data class ActiveStationQueryState(
    val query: StationQuery? = null,
    val cacheState: CachedSnapshotState = CachedSnapshotState.Absent,
)

private data class PendingBlockingFailure(
    val query: StationQuery,
    val reason: StationListFailureReason,
)

private enum class CachedSnapshotState {
    Unknown,
    Present,
    Absent,
}
