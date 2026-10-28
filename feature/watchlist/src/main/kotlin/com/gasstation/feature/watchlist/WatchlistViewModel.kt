package com.gasstation.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import com.gasstation.domain.station.usecase.RemoveWatchedStationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistViewModel @Inject constructor(
    private val observeWatchlist: ObserveWatchlistUseCase,
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val removeWatchedStation: RemoveWatchedStationUseCase,
    savedStateHandle: SavedStateHandle,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val origin = Coordinates(
        latitude = savedStateHandle.requiredCoordinate("latitude"),
        longitude = savedStateHandle.requiredCoordinate("longitude"),
    )
    private val mutableUiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = mutableUiState.asStateFlow()

    private var hasLoggedCompareViewed = false
    private var observationJob: Job? = null
    private var nextRemovalFailureId = 0L

    init {
        observe()
    }

    private fun observe() {
        observationJob?.cancel()
        mutableUiState.update { WatchlistUiState(pendingRemovalFailureId = it.pendingRemovalFailureId) }
        observationJob = observeUserPreferences()
            .map { preferences: UserPreferences -> preferences.fuelType }
            .distinctUntilChanged()
            .flatMapLatest { fuelType ->
                observeWatchlist(WatchlistQuery(origin, fuelType))
                    .map { summaries -> fuelType to summaries }
            }
            .onEach { (fuelType, summaries) ->
                val items = summaries.map(::WatchlistItemUiModel)
                mutableUiState.value = WatchlistUiState(
                    isLoading = false,
                    fuelType = fuelType,
                    stations = items,
                    summary = WatchlistSummaryUiModel.from(items),
                    pendingRemovalFailureId = mutableUiState.value.pendingRemovalFailureId,
                )
                if (!hasLoggedCompareViewed) {
                    hasLoggedCompareViewed = true
                    stationEventLogger.logSafely(
                        StationEvent.CompareViewed(count = items.size),
                    )
                }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                mutableUiState.value = WatchlistUiState(
                    isLoading = false,
                    loadFailed = true,
                    pendingRemovalFailureId = mutableUiState.value.pendingRemovalFailureId,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: WatchlistAction) {
        when (action) {
            WatchlistAction.RetryLoad -> observe()

            is WatchlistAction.RemovalFailureHandled -> mutableUiState.update { state ->
                if (state.pendingRemovalFailureId == action.id) state.copy(pendingRemovalFailureId = null) else state
            }

            is WatchlistAction.RemoveClicked -> viewModelScope.launch {
                try {
                    when (removeWatchedStation(action.stationId)) {
                        WatchMutationResult.Committed -> stationEventLogger.logSafely(
                            StationEvent.WatchToggled(
                                stationId = action.stationId,
                                watched = false,
                            ),
                        )

                        WatchMutationResult.Superseded -> Unit
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    val failureId = nextRemovalFailureId++
                    mutableUiState.update { it.copy(pendingRemovalFailureId = failureId) }
                }
            }
        }
    }
}

private fun SavedStateHandle.requiredCoordinate(key: String): Double {
    val value = checkNotNull<Any>(this[key])
    return when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is String -> value.toDouble()
        else -> error("Expected numeric coordinate for $key but was ${value::class.java.simpleName}")
    }
}
