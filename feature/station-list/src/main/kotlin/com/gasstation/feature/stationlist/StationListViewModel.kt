package com.gasstation.feature.stationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.TogglePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import com.gasstation.feature.stationlist.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class StationListViewModel @Inject constructor(
    private val searchOrchestrator: StationSearchOrchestrator,
    private val updateWatchState: UpdateWatchStateUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val togglePreferredSortOrder: TogglePreferredSortOrderUseCase,
    private val updateSearchRadius: UpdateSearchRadiusUseCase,
    private val updateFuelType: UpdateFuelTypeUseCase,
    private val updateBrandFilter: UpdateBrandFilterUseCase,
    private val locationStateMachine: LocationStateMachine,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val preferenceState = MutableStateFlow<PreferenceLoadState>(PreferenceLoadState.Loading)
    private var preferenceObservationJob: Job? = null
    private var activeRefreshJob: Job? = null
    private var refreshWorkId: Long = 0
    private val preferenceWriteInFlight = AtomicBoolean(false)
    private val transientState = MutableStateFlow(StationListTransientState())
    private val mutableUiState = MutableStateFlow(StationListUiState())
    private val mutableEffects = MutableSharedFlow<StationListEffect>()

    val uiState = mutableUiState.asStateFlow()
    val effects: SharedFlow<StationListEffect> = mutableEffects.asSharedFlow()

    init {
        observePreferences()
        triggerRefreshOnQueryChange()
        bindUiState(searchUiProjection())
    }

    private fun observePreferences() {
        preferenceObservationJob?.cancel()
        preferenceState.value = PreferenceLoadState.Loading
        preferenceObservationJob = observeUserPreferences()
            .onEach { preferenceState.value = PreferenceLoadState.Ready(it) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                preferenceState.value = PreferenceLoadState.Failed
            }
            .launchIn(viewModelScope)
    }

    private fun triggerRefreshOnQueryChange() {
        var previousQuery: StationQuery? = null
        val queryFlow = combine(preferenceState, locationStateMachine.state) { state, location ->
            val preferences = (state as? PreferenceLoadState.Ready)?.preferences
            val coordinates = location.usableCoordinates()
            if (preferences == null || coordinates == null) {
                null
            } else {
                buildQuery(preferences, coordinates)
            }
        }.distinctUntilChanged()
            .onEach { query ->
                if (query == null) {
                    cancelActiveRefresh()
                } else if (searchOrchestrator.shouldRefreshForCriteriaChange(previousQuery, query)) {
                    refreshActiveQuery(query)
                }
                previousQuery = query
            }

        searchOrchestrator.observe(queryFlow)
            .launchIn(viewModelScope)
    }

    private fun searchUiProjection(): Flow<StationListSearchUiProjection> = searchOrchestrator.searchResult
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

    private fun bindUiState(searchUiProjection: Flow<StationListSearchUiProjection>) {
        combine(
            preferenceState,
            locationStateMachine.state,
            transientState,
            searchUiProjection,
            searchOrchestrator.blockingFailure,
        ) { currentPreferenceState, location, transient, resultProjection, blockingFailure ->
            val readyPreferences =
                (currentPreferenceState as? PreferenceLoadState.Ready)?.preferences
            StationListUiState(
                currentCoordinates = location.currentCoordinates,
                currentAddressLabel = location.currentAddressLabel,
                permissionState = location.permissionState,
                needsRecoveryRefresh = location.needsRecoveryRefresh,
                isGpsEnabled = location.isGpsEnabled,
                isAvailabilityKnown = location.isAvailabilityKnown,
                isLoading =
                transient.isLoading ||
                    currentPreferenceState is PreferenceLoadState.Loading,
                isRefreshing = transient.isRefreshing,
                isStale = resultProjection.freshness is StationFreshness.Stale,
                blockingFailure = blockingFailure,
                stations = resultProjection.stations,
                preferences = readyPreferences,
                preferenceLoadFailed = currentPreferenceState is PreferenceLoadState.Failed,
                pendingPreferenceWrite = transient.pendingPreferenceWrite,
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

            StationListAction.RefreshRequested -> refresh(
                showPermissionDeniedFeedback = true,
            )

            StationListAction.RetryClicked -> {
                if (preferenceState.value is PreferenceLoadState.Failed) {
                    observePreferences()
                } else {
                    refresh(showPermissionDeniedFeedback = true)
                }
            }

            StationListAction.SortToggleRequested -> updatePreference {
                togglePreferredSortOrder()
            }

            is StationListAction.SearchRadiusSelected -> updatePreference {
                updateSearchRadius(action.radius)
            }

            is StationListAction.FuelTypeSelected -> updatePreference {
                updateFuelType(action.fuelType)
            }

            is StationListAction.BrandFilterSelected -> updatePreference {
                updateBrandFilter(action.brandFilter)
            }

            is StationListAction.WatchToggled -> toggleWatchState(
                stationId = action.stationId,
                watched = action.watched,
            )

            is StationListAction.PermissionChanged -> {
                locationStateMachine.onPermissionChanged(action.permissionState)
                if (action.permissionState == LocationPermissionState.Denied) {
                    cancelActiveRefresh()
                }
            }

            is StationListAction.GpsAvailabilityChanged -> {
                locationStateMachine.onGpsAvailabilityChanged(action.isEnabled)
                if (!action.isEnabled) {
                    cancelActiveRefresh()
                }
            }

            is StationListAction.StationClicked -> {
                viewModelScope.launch {
                    val currentCoordinates = locationStateMachine.state.value.usableCoordinates()
                        ?: return@launch
                    val preferences = readyPreferencesOrNull() ?: return@launch
                    val provider = preferences.mapProvider
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
                            originLatitude = currentCoordinates.latitude,
                            originLongitude = currentCoordinates.longitude,
                            latitude = action.station.latitude,
                            longitude = action.station.longitude,
                        ),
                    )
                }
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
        launchRefreshWork {
            val location = locationStateMachine.state.value
            if (location.permissionState == LocationPermissionState.Denied) {
                if (showPermissionDeniedFeedback) {
                    mutableEffects.emit(
                        StationListEffect.ShowSnackbar(
                            StringResource.fromId(R.string.station_list_permission_denied),
                        ),
                    )
                }
                return@launchRefreshWork
            }
            if (readyPreferencesOrNull() == null) return@launchRefreshWork
            if (!location.isGpsEnabled) {
                mutableEffects.emit(StationListEffect.OpenLocationSettings)
                return@launchRefreshWork
            }

            transientState.update {
                it.copy(
                    isLoading = location.currentCoordinates == null,
                    isRefreshing = true,
                )
            }

            val coordinates = handleLocationResult(
                locationStateMachine.acquireLocation(),
                showPermissionDeniedFeedback = showPermissionDeniedFeedback,
            ) ?: return@launchRefreshWork
            if (!locationStateMachine.state.value.hasEligibleCoordinates(coordinates)) {
                return@launchRefreshWork
            }

            refreshAddressLabel(coordinates)

            val preferences = readyPreferencesOrNull() ?: return@launchRefreshWork
            val query = buildQuery(preferences, coordinates)
            if (!isRefreshQueryEligible(query)) {
                return@launchRefreshWork
            }
            when (val outcome = searchOrchestrator.refresh(query)) {
                RefreshOutcome.Success -> Unit
                is RefreshOutcome.Failed -> handleRefreshFailure(query, outcome.reason)
            }
        }
    }

    private fun readyPreferencesOrNull(): UserPreferences? = (preferenceState.value as? PreferenceLoadState.Ready)?.preferences

    private fun isRefreshQueryEligible(query: StationQuery): Boolean {
        val latestPreferences = readyPreferencesOrNull() ?: return false
        return locationStateMachine.state.value.hasEligibleCoordinates(query.coordinates) &&
            buildQuery(latestPreferences, query.coordinates) == query
    }

    private fun refreshAddressLabel(coordinates: Coordinates) {
        viewModelScope.launch {
            locationStateMachine.resolveAddressLabel(coordinates)
        }
    }

    private suspend fun handleLocationResult(result: LocationAcquisitionResult, showPermissionDeniedFeedback: Boolean): Coordinates? =
        when (result) {
            is LocationAcquisitionResult.Success -> {
                searchOrchestrator.clearBlockingFailure()
                result.coordinates
            }

            LocationAcquisitionResult.Superseded -> null

            LocationAcquisitionResult.PermissionDenied -> {
                logLocationFailure(result)
                if (showPermissionDeniedFeedback) {
                    mutableEffects.emit(
                        StationListEffect.ShowSnackbar(
                            StringResource.fromId(R.string.station_list_permission_denied),
                        ),
                    )
                }
                null
            }

            LocationAcquisitionResult.TimedOut -> {
                logLocationFailure(result)
                onBlockingFailure(
                    reason = StationListFailureReason.LocationTimedOut,
                    message = StringResource.fromId(R.string.station_list_location_timeout),
                )
                null
            }

            LocationAcquisitionResult.Unavailable,
            is LocationAcquisitionResult.Error,
            -> {
                logLocationFailure(result)
                onBlockingFailure(
                    reason = StationListFailureReason.LocationFailed,
                    message = StringResource.fromId(R.string.station_list_location_failed),
                )
                null
            }
        }

    private suspend fun handleRefreshFailure(query: StationQuery, reason: StationRefreshFailureReason?) {
        if (searchOrchestrator.activeQueryState.value.query != query) return

        reason?.let {
            stationEventLogger.logSafely(StationEvent.RefreshFailed(reason = it))
        }
        searchOrchestrator.onRefreshFailure(query = query, reason = reason)
        mutableEffects.emit(StationListEffect.ShowSnackbar(reason.refreshFailureResource()))
    }

    private suspend fun onBlockingFailure(reason: StationListFailureReason, message: StringResource) {
        searchOrchestrator.onBlockingFailure(reason = reason)
        mutableEffects.emit(StationListEffect.ShowSnackbar(message))
    }

    private fun logLocationFailure(result: LocationAcquisitionResult) {
        result.failureEventType()?.let { resultType ->
            stationEventLogger.logSafely(StationEvent.LocationFailed(resultType = resultType))
        }
    }

    private fun refreshActiveQuery(query: StationQuery) {
        launchRefreshWork {
            if (!locationStateMachine.state.value.hasEligibleCoordinates(query.coordinates)) {
                return@launchRefreshWork
            }
            transientState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = true,
                )
            }

            when (val outcome = searchOrchestrator.refresh(query)) {
                RefreshOutcome.Success -> Unit

                is RefreshOutcome.Failed -> {
                    handleRefreshFailure(query, outcome.reason)
                }
            }
        }
    }

    private fun launchRefreshWork(block: suspend () -> Unit) {
        activeRefreshJob?.cancel()
        val workId = ++refreshWorkId
        val job = viewModelScope.launch {
            try {
                block()
            } finally {
                if (refreshWorkId == workId) {
                    activeRefreshJob = null
                    finishRefreshIndicators()
                }
            }
        }
        activeRefreshJob = job.takeIf { it.isActive }
    }

    private fun cancelActiveRefresh() {
        if (activeRefreshJob == null) return
        refreshWorkId += 1
        activeRefreshJob?.cancel()
        activeRefreshJob = null
        finishRefreshIndicators()
    }

    private fun finishRefreshIndicators() {
        transientState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
            )
        }
    }

    private fun updatePreference(update: suspend () -> UserPreferences) {
        if (preferenceState.value !is PreferenceLoadState.Ready) return
        if (!preferenceWriteInFlight.compareAndSet(false, true)) return
        transientState.update { it.copy(pendingPreferenceWrite = true) }
        viewModelScope.launch {
            try {
                preferenceState.value = PreferenceLoadState.Ready(update())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableEffects.emit(
                    StationListEffect.ShowSnackbar(
                        StringResource.fromId(R.string.station_list_preference_save_failed),
                    ),
                )
            } finally {
                transientState.update { it.copy(pendingPreferenceWrite = false) }
                preferenceWriteInFlight.set(false)
            }
        }
    }

    private fun toggleWatchState(stationId: String, watched: Boolean) {
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

    private fun buildQuery(preferences: UserPreferences, coordinates: Coordinates): StationQuery = StationQuery(
        coordinates = coordinates,
        radius = preferences.searchRadius,
        fuelType = preferences.fuelType,
        brandFilter = preferences.brandFilter,
        sortOrder = preferences.sortOrder,
    )
}

private sealed interface PreferenceLoadState {
    data object Loading : PreferenceLoadState

    data class Ready(val preferences: UserPreferences) : PreferenceLoadState

    data object Failed : PreferenceLoadState
}

private data class StationListTransientState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val pendingPreferenceWrite: Boolean = false,
)

private data class StationListSearchUiProjection(
    val sourceStations: List<StationListEntry> = emptyList(),
    val stations: List<StationListItemUiModel> = emptyList(),
    val freshness: StationFreshness = StationFreshness.Stale,
    val fetchedAt: Instant? = null,
)

private fun LocationState.usableCoordinates(): Coordinates? = currentCoordinates?.takeIf {
    permissionState != LocationPermissionState.Denied && isGpsEnabled
}

private fun LocationState.hasEligibleCoordinates(coordinates: Coordinates): Boolean = permissionState != LocationPermissionState.Denied &&
    isAvailabilityKnown &&
    isGpsEnabled &&
    currentCoordinates == coordinates

private fun LocationAcquisitionResult.failureEventType(): String? = when (this) {
    is LocationAcquisitionResult.Success -> null
    LocationAcquisitionResult.Superseded -> null
    LocationAcquisitionResult.PermissionDenied -> "PermissionDenied"
    LocationAcquisitionResult.TimedOut -> "TimedOut"
    LocationAcquisitionResult.Unavailable -> "Unavailable"
    is LocationAcquisitionResult.Error -> "Error"
}

private fun StationRefreshFailureReason?.refreshFailureResource(): StringResource = when (this) {
    StationRefreshFailureReason.Timeout -> StringResource.fromId(R.string.station_list_refresh_timeout)

    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    is StationRefreshFailureReason.Http,
    StationRefreshFailureReason.Unknown,
    null,
    -> StringResource.fromId(R.string.station_list_refresh_failed)
}
