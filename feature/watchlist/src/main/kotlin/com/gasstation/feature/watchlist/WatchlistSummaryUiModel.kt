package com.gasstation.feature.watchlist

import java.time.Instant

data class WatchlistSummaryUiModel(val count: Int = 0, val averagePriceWon: Int? = null, val latestSeenAt: Instant? = null) {
    companion object {
        fun from(items: List<WatchlistItemUiModel>): WatchlistSummaryUiModel {
            if (items.isEmpty()) return WatchlistSummaryUiModel()

            val count = items.size
            val sum = items.sumOf { it.priceWon.toLong() }
            return WatchlistSummaryUiModel(
                count = count,
                averagePriceWon = ((sum + count / 2L) / count).toInt(),
                latestSeenAt = items.mapNotNull(WatchlistItemUiModel::lastSeenAt).maxOrNull(),
            )
        }
    }
}
