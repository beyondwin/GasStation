package com.gasstation.feature.watchlist

sealed interface WatchlistAction {
    data object RetryLoad : WatchlistAction
    data class RemoveClicked(val stationId: String) : WatchlistAction
}
