package com.gasstation.feature.stationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.core.model.SortOrder
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
    private val locationStateMachine: LocationStateMachine,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val preferences = MutableStateFlow(UserPreferences.default())
    private val transientState = MutableStateFlow(StationListTransientState())
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

        combine(preferences, locationStateMachine.state) { prefs, location ->
            location.usableCoordinates()
                ?.let { coordinates -> buildQuery(preferences = prefs, coordinates = coordinates) }
        }.distinctUntilChanged()
            .onEach { query ->
                val previousQuery = activeQueryState.value.query
                activeQueryState.value = ActiveStationQueryState(
                    query = query,
                    cacheState = if (query == null) CachedSnapshotState.Absent else CachedSnapshotState.Unknown,
                )
                if (previousQuery != query) {
                    pendingBlockingFailure.value = null
                    transientState.update { it.copy(blockingFailure = null) }
                }
                if (previousQuery.shouldRefreshForCriteriaChange(query) && query != null) {
                    refreshActiveQuery(query)
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

        combine(
            preferences,
            locationStateMachine.state,
            transientState,
            searchResult,
        ) { prefs, location, transient, result ->
            StationListUiState(
                currentCoordinates = location.currentCoordinates,
                currentAddressLabel = location.currentAddressLabel,
                permissionState = location.permissionState,
                hasDeniedLocationAccess = location.hasDeniedLocationAccess,
                needsRecoveryRefresh = location.needsRecoveryRefresh,
                isGpsEnabled = location.isGpsEnabled,
                isAvailabilityKnown = location.isAvailabilityKnown,
                isLoading = transient.isLoading,
                isRefreshing = transient.isRefreshing,
                isStale = result.freshness is StationFreshness.Stale,
                blockingFailure = transient.blockingFailure,
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

            is StationListAction.PermissionChanged -> locationStateMachine.onPermissionChanged(action.permissionState)

            is StationListAction.GpsAvailabilityChanged -> locationStateMachine.onGpsAvailabilityChanged(action.isEnabled)

            is StationListAction.StationClicked -> viewModelScope.launch {
                val currentCoordinates = locationStateMachine.state.value.currentCoordinates
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
        (flowOverride ?: locationStateMachine.observeGpsAvailability())
            .collect { isEnabled ->
                onAction(StationListAction.GpsAvailabilityChanged(isEnabled))
            }
    }

    private fun refresh(
        showPermissionDeniedFeedback: Boolean,
    ) {
        viewModelScope.launch {
            val location = locationStateMachine.state.value
            if (!location.isGpsEnabled) {
                mutableEffects.emit(StationListEffect.OpenLocationSettings)
                return@launch
            }

            transientState.update {
                it.copy(
                    isLoading = location.currentCoordinates == null,
                    isRefreshing = true,
                )
            }

            try {
                val coordinates = handleLocationResult(
                    locationStateMachine.acquireLocation(),
                    showPermissionDeniedFeedback = showPermissionDeniedFeedback,
                ) ?: return@launch

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
                        transientState.update { current -> current.copy(blockingFailure = null) }
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (throwable: Throwable) {
                    handleRefreshFailure(query, (throwable as? StationRefreshException)?.reason)
                }
            } finally {
                transientState.update {
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
            val addressLabel = locationStateMachine.resolveAddressLabel(coordinates)
            locationStateMachine.onAddressResolved(coordinates, addressLabel)
        }
    }

    private suspend fun handleLocationResult(
        result: LocationAcquisitionResult,
        showPermissionDeniedFeedback: Boolean,
    ): Coordinates? = when (result) {
        is LocationAcquisitionResult.Success -> {
            transientState.update { it.copy(blockingFailure = null) }
            result.coordinates
        }

        LocationAcquisitionResult.PermissionDenied -> {
            logLocationFailure(result)
            if (showPermissionDeniedFeedback) {
                mutableEffects.emit(StationListEffect.ShowSnackbar("위치 권한을 허용해주세요."))
            }
            null
        }

        LocationAcquisitionResult.TimedOut -> {
            logLocationFailure(result)
            onBlockingFailure(
                reason = StationListFailureReason.LocationTimedOut,
                message = "현재 위치 확인이 지연되고 있습니다.",
            )
            null
        }

        LocationAcquisitionResult.Unavailable,
        is LocationAcquisitionResult.Error -> {
            logLocationFailure(result)
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
                transientState.update { it.copy(blockingFailure = null) }
            }

            CachedSnapshotState.Absent -> {
                pendingBlockingFailure.value = null
                transientState.update { it.copy(blockingFailure = reason) }
            }

            CachedSnapshotState.Unknown -> {
                pendingBlockingFailure.value = query?.let { PendingBlockingFailure(it, reason) }
            }
        }
        mutableEffects.emit(StationListEffect.ShowSnackbar(message))
    }

    private fun logLocationFailure(result: LocationAcquisitionResult) {
        result.failureEventType()?.let { resultType ->
            stationEventLogger.log(StationEvent.LocationFailed(resultType = resultType))
        }
    }

    private fun refreshActiveQuery(query: StationQuery) {
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = true,
                )
            }

            try {
                refreshNearbyStations(query)
                if (activeQueryState.value.query == query) {
                    pendingBlockingFailure.value = null
                    transientState.update { current -> current.copy(blockingFailure = null) }
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                handleRefreshFailure(query, (throwable as? StationRefreshException)?.reason)
            } finally {
                transientState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    private fun syncBlockingFailureWithObservedResult(hasCachedSnapshot: Boolean) {
        if (hasCachedSnapshot) {
            pendingBlockingFailure.value = null
            transientState.update { it.copy(blockingFailure = null) }
            return
        }

        val activeQuery = activeQueryState.value.query ?: return
        val pendingFailure = pendingBlockingFailure.value
            ?.takeIf { it.query == activeQuery }
            ?: return
        pendingBlockingFailure.value = null
        transientState.update { it.copy(blockingFailure = pendingFailure.reason) }
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

private data class StationListTransientState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
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

private fun StationQuery?.shouldRefreshForCriteriaChange(next: StationQuery?): Boolean =
    this != null &&
        next != null &&
        coordinates == next.coordinates &&
        (radius != next.radius ||
            fuelType != next.fuelType ||
            brandFilter != next.brandFilter ||
            sortOrder != next.sortOrder)

private fun LocationState.usableCoordinates(): Coordinates? =
    currentCoordinates?.takeIf {
        isGpsEnabled &&
            (
                permissionState != LocationPermissionState.Denied ||
                    hasDeniedLocationAccess
                )
    }

private fun LocationAcquisitionResult.failureEventType(): String? = when (this) {
    is LocationAcquisitionResult.Success -> null
    LocationAcquisitionResult.PermissionDenied -> "PermissionDenied"
    LocationAcquisitionResult.TimedOut -> "TimedOut"
    LocationAcquisitionResult.Unavailable -> "Unavailable"
    is LocationAcquisitionResult.Error -> "Error"
}
