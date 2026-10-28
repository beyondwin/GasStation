package com.gasstation.feature.watchlist

sealed interface WatchlistAction {
    data object RetryLoad : WatchlistAction
    data class RemovalFailureHandled(val id: Long) : WatchlistAction
    data class RemoveClicked(val stationId: String) : WatchlistAction
}
