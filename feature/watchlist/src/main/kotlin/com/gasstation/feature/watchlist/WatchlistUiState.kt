package com.gasstation.feature.watchlist

data class WatchlistUiState(
    val stations: List<WatchlistItemUiModel> = emptyList(),
    val summary: WatchlistSummaryUiModel = WatchlistSummaryUiModel.from(stations),
)
