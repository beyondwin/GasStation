package com.gasstation.feature.stationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class StationListViewModel @Inject constructor(
    private val searchOrchestrator: StationSearchOrchestrator,
    private val updateWatchState: UpdateWatchStateUseCase,
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val locationStateMachine: LocationStateMachine,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val preferences = MutableStateFlow(UserPreferences.default())
    private val transientState = MutableStateFlow(StationListTransientState())
    private val mutableUiState = MutableStateFlow(StationListUiState())
    private val mutableEffects = MutableSharedFlow<StationListEffect>()

    val uiState = mutableUiState.asStateFlow()
    val effects: SharedFlow<StationListEffect> = mutableEffects.asSharedFlow()

    init {
        observeUserPreferences()
            .onEach { preferences.value = it }
            .launchIn(viewModelScope)

        var previousQuery: StationQuery? = null
        val queryFlow = combine(preferences, locationStateMachine.state) { prefs, location ->
            location.usableCoordinates()
                ?.let { coordinates -> buildQuery(preferences = prefs, coordinates = coordinates) }
        }.distinctUntilChanged()
            .onEach { query ->
                if (searchOrchestrator.shouldRefreshForCriteriaChange(previousQuery, query) && query != null) {
                    refreshActiveQuery(query)
                }
                previousQuery = query
            }

        searchOrchestrator.observe(queryFlow)
            .launchIn(viewModelScope)

        val searchUiProjection = searchOrchestrator.searchResult
            .runningFold(StationListSearchUiProjection()) { previous, result ->
                val stationItems = if (previous.sourceStations == result.stations) {
                    previous.stations
                } else {
                    result.stations.map(::StationListItemUiModel)
                }
                StationListSearchUiProjection(
                    sourceStations = result.stations,
                    stations = stationItems,
                    freshness = result.freshness,
                    fetchedAt = result.fetchedAt,
                )
            }
            .drop(1)
            .distinctUntilChanged()

        combine(
            preferences,
            locationStateMachine.state,
            transientState,
            searchUiProjection,
            searchOrchestrator.blockingFailure,
        ) { prefs, location, transient, resultProjection, blockingFailure ->
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
                isStale = resultProjection.freshness is StationFreshness.Stale,
                blockingFailure = blockingFailure,
                stations = resultProjection.stations,
                selectedBrandFilter = prefs.brandFilter,
                selectedRadius = prefs.searchRadius,
                selectedFuelType = prefs.fuelType,
                selectedSortOrder = prefs.sortOrder,
                lastUpdatedAt = resultProjection.fetchedAt,
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
                val provider = preferences.value.mapProvider
                stationEventLogger.logSafely(
                    StationEvent.ExternalMapOpened(
                        stationId = action.station.id,
                        provider = provider,
                    ),
                )
                mutableEffects.emit(
                    StationListEffect.OpenExternalMap(
                        provider = provider,
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

    suspend fun collectLocationAvailability(flowOverride: Flow<Boolean>? = null) {
        (flowOverride ?: locationStateMachine.observeGpsAvailability())
            .collect { isEnabled ->
                onAction(StationListAction.GpsAvailabilityChanged(isEnabled))
            }
    }

    private fun refresh(showPermissionDeniedFeedback: Boolean) {
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
                when (val outcome = searchOrchestrator.refresh(query)) {
                    RefreshOutcome.Success -> Unit
                    is RefreshOutcome.Failed -> handleRefreshFailure(query, outcome.reason)
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
            searchOrchestrator.clearBlockingFailure()
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
        if (searchOrchestrator.activeQueryState.value.query != query) return

        reason?.let {
            stationEventLogger.logSafely(StationEvent.RefreshFailed(reason = it))
        }
        searchOrchestrator.onRefreshFailure(query = query, reason = reason)
        mutableEffects.emit(StationListEffect.ShowSnackbar(reason.refreshFailureMessage()))
    }

    private suspend fun onBlockingFailure(
        reason: StationListFailureReason,
        message: String,
    ) {
        searchOrchestrator.onBlockingFailure(reason = reason)
        mutableEffects.emit(StationListEffect.ShowSnackbar(message))
    }

    private fun logLocationFailure(result: LocationAcquisitionResult) {
        result.failureEventType()?.let { resultType ->
            stationEventLogger.logSafely(StationEvent.LocationFailed(resultType = resultType))
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
                when (val outcome = searchOrchestrator.refresh(query)) {
                    RefreshOutcome.Success -> Unit
                    is RefreshOutcome.Failed -> {
                        handleRefreshFailure(query, outcome.reason)
                    }
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
            val entry = searchOrchestrator.searchResult.value.stations
                .firstOrNull { it.station.id == stationId }
                ?: return@launch
            updateWatchState(entry.station, watched)
            stationEventLogger.logSafely(
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
)

private data class StationListSearchUiProjection(
    val sourceStations: List<StationListEntry> = emptyList(),
    val stations: List<StationListItemUiModel> = emptyList(),
    val freshness: StationFreshness = StationFreshness.Stale,
    val fetchedAt: Instant? = null,
)

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

private fun StationRefreshFailureReason?.refreshFailureMessage(): String = when (this) {
    StationRefreshFailureReason.Timeout -> "서버 응답이 늦어 가격을 새로고침하지 못했습니다."
    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    StationRefreshFailureReason.Unknown,
    null -> "주유소 목록을 새로고침하지 못했습니다."
}
