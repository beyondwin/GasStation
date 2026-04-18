package com.gasstation.feature.watchlist

data class WatchlistUiState(
    val stations: List<WatchlistItemUiModel> = emptyList(),
)
