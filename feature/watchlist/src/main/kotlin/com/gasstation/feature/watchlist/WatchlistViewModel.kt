package com.gasstation.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    savedStateHandle: SavedStateHandle,
    private val stationEventLogger: StationEventLogger,
) : ViewModel() {
    private val origin = Coordinates(
        latitude = savedStateHandle.requiredCoordinate("latitude"),
        longitude = savedStateHandle.requiredCoordinate("longitude"),
    )
    private var hasLoggedCompareViewed = false

    val uiState = observeWatchlist(origin)
        .map { summaries ->
            if (!hasLoggedCompareViewed) {
                hasLoggedCompareViewed = true
                stationEventLogger.logSafely(StationEvent.CompareViewed(count = summaries.size))
            }
            WatchlistUiState(stations = summaries.map(::WatchlistItemUiModel))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchlistUiState(),
        )
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
