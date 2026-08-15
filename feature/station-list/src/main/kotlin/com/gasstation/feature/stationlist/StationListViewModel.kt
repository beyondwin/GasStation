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
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import com.gasstation.feature.stationlist.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class StationListViewModel @Inject internal constructor(
    private val searchOrchestrator: StationSearchOrchestrator,
    private val updateWatchState: UpdateWatchStateUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val togglePreferredSortOrder: TogglePreferredSortOrderUseCase,
    private val updateSearchRadius: UpdateSearchRadiusUseCase,
    private val updateFuelType: UpdateFuelTypeUseCase,
    private val updateBrandFilter: UpdateBrandFilterUseCase,
    private val locationStateMachine: LocationStateMachine,
    private val refreshCoordinator: RefreshCoordinator,
    private val stationEventLogger: StationEventLogger,
    private val commandQueue: StationListCommandQueue,
) : ViewModel() {
    private val preferenceState = MutableStateFlow<PreferenceLoadState>(PreferenceLoadState.Loading)
    private var preferenceObservationJob: Job? = null
    private val preferenceWriteInFlight = AtomicBoolean(false)
    private val preferenceMutationState = MutableStateFlow(StationListPreferenceMutationState())
    private val mutableUiState = MutableStateFlow(StationListUiState())

    val uiState = mutableUiState.asStateFlow()

    init {
        observePreferences()
        observeSearch()
        bindUiState()
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

    private fun observeSearch() {
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
                    refreshCoordinator.cancel()
                } else if (refreshCoordinator.requiresRefresh(previousQuery, query)) {
                    requestRefresh(RefreshRequest.ActiveQuery(query))
                }
                previousQuery = query
            }

        searchOrchestrator.observe(queryFlow)
            .launchIn(viewModelScope)
    }

    private fun searchUiProjection(): Flow<StationListSearchProjection> = searchOrchestrator.searchResult
        .runningFold(StationListSearchProjection(), ::projectStationSearchResult)
        .drop(1)
        .distinctUntilChanged()

    private fun bindUiState() {
        val baseInputs = combine(
            preferenceState,
            preferenceMutationState,
            locationStateMachine.state,
            refreshCoordinator.state,
            searchUiProjection(),
        ) { preference, preferenceMutation, location, refresh, search ->
            StationListBaseStateInputs(
                preference = preference,
                preferenceMutation = preferenceMutation,
                location = location,
                refresh = refresh,
                search = search,
            )
        }
        combine(
            baseInputs,
            searchOrchestrator.blockingFailure,
            commandQueue.commands,
        ) { base, blockingFailure, commands ->
            StationListStateAssembler.assemble(
                StationListStateInputs(
                    preference = base.preference,
                    preferenceMutation = base.preferenceMutation,
                    location = base.location,
                    refresh = base.refresh,
                    search = base.search,
                    blockingFailure = blockingFailure,
                    pendingCommands = commands,
                ),
            )
        }.onEach { assembledState -> mutableUiState.value = assembledState }
            .launchIn(viewModelScope)
    }

    fun onAction(action: StationListAction) {
        when (action) {
            StationListAction.AutoRefreshRequested -> refresh(showPermissionDeniedFeedback = false)

            StationListAction.RefreshRequested -> refresh(showPermissionDeniedFeedback = true)

            StationListAction.RetryClicked -> {
                if (preferenceState.value is PreferenceLoadState.Failed) {
                    observePreferences()
                } else if (searchOrchestrator.observationFailed.value) {
                    searchOrchestrator.retryObservation()
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

            is StationListAction.WatchToggled -> toggleWatchState(action.stationId, action.watched)

            is StationListAction.PermissionChanged -> {
                locationStateMachine.onPermissionChanged(action.permissionState)
                if (action.permissionState == LocationPermissionState.Denied) refreshCoordinator.cancel()
            }

            is StationListAction.GpsAvailabilityChanged -> {
                locationStateMachine.onGpsAvailabilityChanged(action.isEnabled)
                if (!action.isEnabled) refreshCoordinator.cancel()
            }

            is StationListAction.CommandHandled -> commandQueue.acknowledge(action.commandId)

            is StationListAction.StationClicked -> {
                viewModelScope.launch {
                    val currentCoordinates = locationStateMachine.state.value.usableCoordinates()
                        ?: return@launch
                    val preferences = readyPreferencesOrNull() ?: return@launch
                    val provider = preferences.mapProvider
                    commandQueue.enqueue(
                        StationListCommandPayload.OpenExternalMap(
                            provider = provider,
                            stationName = action.station.name,
                            originLatitude = currentCoordinates.latitude,
                            originLongitude = currentCoordinates.longitude,
                            latitude = action.station.latitude,
                            longitude = action.station.longitude,
                        ),
                    )
                    stationEventLogger.logSafely(
                        StationEvent.ExternalMapOpened(
                            stationId = action.station.id,
                            provider = provider,
                        ),
                    )
                }
            }
        }
    }

    suspend fun collectLocationAvailability(flowOverride: Flow<Boolean>? = null) {
        (flowOverride ?: locationStateMachine.observeGpsAvailability()).collect { isEnabled ->
            onAction(StationListAction.GpsAvailabilityChanged(isEnabled))
        }
    }

    private fun refresh(showPermissionDeniedFeedback: Boolean) {
        if (readyPreferencesOrNull() == null) return
        requestRefresh(RefreshRequest.AcquireLocation(showPermissionDeniedFeedback = showPermissionDeniedFeedback))
    }

    private fun readyPreferencesOrNull(): UserPreferences? = (preferenceState.value as? PreferenceLoadState.Ready)?.preferences

    private fun latestEligibleQuery(): StationQuery? {
        val preferences = readyPreferencesOrNull() ?: return null
        val coordinates = locationStateMachine.state.value.usableCoordinates() ?: return null
        return buildQuery(preferences, coordinates)
    }

    private fun requestRefresh(request: RefreshRequest) = refreshCoordinator.request(
        scope = viewModelScope,
        request = request,
        latestEligibleQuery = ::latestEligibleQuery,
        onResult = ::onRefreshResult,
    )

    private fun onRefreshResult(result: RefreshCoordinatorResult) {
        when (result) {
            is RefreshCoordinatorResult.PermissionRequired -> {
                if (result.showFeedback) {
                    commandQueue.enqueue(
                        StationListCommandPayload.ShowSnackbar(
                            StringResource.fromId(R.string.station_list_permission_denied),
                        ),
                    )
                }
            }

            RefreshCoordinatorResult.GpsDisabled -> commandQueue.enqueue(StationListCommandPayload.OpenLocationSettings)

            is RefreshCoordinatorResult.LocationAcquired -> searchOrchestrator.clearBlockingFailure()

            is RefreshCoordinatorResult.LocationAcquisitionFailed -> handleLocationFailure(
                result = result.result,
                showPermissionDeniedFeedback = result.showPermissionDeniedFeedback,
            )

            is RefreshCoordinatorResult.RefreshStarting -> searchOrchestrator.ensureActiveQuery(result.query)

            is RefreshCoordinatorResult.RefreshSucceeded -> searchOrchestrator.onRefreshSucceeded(result.query)

            is RefreshCoordinatorResult.RefreshFailed -> {
                result.reason?.let { reason ->
                    stationEventLogger.logSafely(StationEvent.RefreshFailed(reason = reason))
                }
                searchOrchestrator.onRefreshFailure(query = result.query, reason = result.reason)
                commandQueue.enqueue(
                    StationListCommandPayload.ShowSnackbar(result.reason.refreshFailureResource()),
                )
            }
        }
    }

    private fun handleLocationFailure(result: LocationAcquisitionResult, showPermissionDeniedFeedback: Boolean) {
        when (result) {
            is LocationAcquisitionResult.Success,
            LocationAcquisitionResult.Superseded,
            -> Unit

            LocationAcquisitionResult.PermissionDenied -> {
                logLocationFailure(result)
                if (showPermissionDeniedFeedback) {
                    commandQueue.enqueue(
                        StationListCommandPayload.ShowSnackbar(
                            StringResource.fromId(R.string.station_list_permission_denied),
                        ),
                    )
                }
            }

            LocationAcquisitionResult.TimedOut -> {
                logLocationFailure(result)
                onBlockingFailure(
                    reason = StationListFailureReason.LocationTimedOut,
                    message = StringResource.fromId(R.string.station_list_location_timeout),
                )
            }

            LocationAcquisitionResult.Unavailable,
            is LocationAcquisitionResult.Error,
            -> {
                logLocationFailure(result)
                onBlockingFailure(
                    reason = StationListFailureReason.LocationFailed,
                    message = StringResource.fromId(R.string.station_list_location_failed),
                )
            }
        }
    }

    private fun onBlockingFailure(reason: StationListFailureReason, message: StringResource) {
        searchOrchestrator.onBlockingFailure(reason = reason)
        commandQueue.enqueue(StationListCommandPayload.ShowSnackbar(message))
    }

    private fun logLocationFailure(result: LocationAcquisitionResult) = result.failureEventType()?.let { resultType ->
        stationEventLogger.logSafely(StationEvent.LocationFailed(resultType = resultType))
    }

    private fun updatePreference(update: suspend () -> UserPreferences) {
        if (preferenceState.value !is PreferenceLoadState.Ready) return
        if (!preferenceWriteInFlight.compareAndSet(false, true)) return
        preferenceMutationState.update { it.copy(pendingPreferenceWrite = true) }
        viewModelScope.launch {
            try {
                preferenceState.value = PreferenceLoadState.Ready(update())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                commandQueue.enqueue(
                    StationListCommandPayload.ShowSnackbar(
                        StringResource.fromId(R.string.station_list_preference_save_failed),
                    ),
                )
            } finally {
                preferenceMutationState.update { it.copy(pendingPreferenceWrite = false) }
                preferenceWriteInFlight.set(false)
            }
        }
    }

    private fun toggleWatchState(stationId: String, watched: Boolean) {
        viewModelScope.launch {
            val entry = searchOrchestrator.searchResult.value.stations
                .firstOrNull { it.station.id == stationId }
                ?: return@launch
            when (updateWatchState(entry.station, watched)) {
                WatchMutationResult.Committed -> stationEventLogger.logSafely(
                    StationEvent.WatchToggled(
                        stationId = stationId,
                        watched = watched,
                    ),
                )

                WatchMutationResult.Superseded -> Unit
            }
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

private fun LocationState.usableCoordinates(): Coordinates? = currentCoordinates?.takeIf {
    permissionState != LocationPermissionState.Denied && isAvailabilityKnown && isGpsEnabled
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
