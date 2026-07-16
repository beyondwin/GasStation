package com.gasstation.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import com.gasstation.domain.station.usecase.UpdateWatchStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    private val updateWatchState: UpdateWatchStateUseCase,
    savedStateHandle: SavedStateHandle,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val origin = Coordinates(
        latitude = savedStateHandle.requiredCoordinate("latitude"),
        longitude = savedStateHandle.requiredCoordinate("longitude"),
    )
    private var hasLoggedCompareViewed = false
    private var stationsById: Map<String, Station> = emptyMap()

    val uiState = observeWatchlist(origin)
        .map { summaries ->
            stationsById = summaries.associate { it.station.id to it.station }
            if (!hasLoggedCompareViewed) {
                hasLoggedCompareViewed = true
                stationEventLogger.logSafely(StationEvent.CompareViewed(count = summaries.size))
            }
            val stations = summaries.map(::WatchlistItemUiModel)
            WatchlistUiState(
                stations = stations,
                summary = WatchlistSummaryUiModel.from(stations),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchlistUiState(),
        )

    fun onAction(action: WatchlistAction) {
        when (action) {
            is WatchlistAction.RemoveClicked -> {
                val station = stationsById[action.stationId] ?: return
                viewModelScope.launch {
                    updateWatchState(station, false)
                    stationEventLogger.logSafely(
                        StationEvent.WatchToggled(stationId = station.id, watched = false),
                    )
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
