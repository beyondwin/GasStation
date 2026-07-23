package com.gasstation.feature.watchlist

import com.gasstation.core.model.FuelType

data class WatchlistUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val fuelType: FuelType? = null,
    val stations: List<WatchlistItemUiModel> = emptyList(),
    val summary: WatchlistSummaryUiModel = WatchlistSummaryUiModel(),
)
