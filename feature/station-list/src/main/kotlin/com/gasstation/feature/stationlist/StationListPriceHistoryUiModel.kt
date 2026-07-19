package com.gasstation.feature.stationlist

import com.gasstation.domain.station.model.StationPriceDelta

sealed interface StationListPriceHistoryUiModel {
    data object Unavailable : StationListPriceHistoryUiModel

    data object Unchanged : StationListPriceHistoryUiModel

    data class Increased(val amountWon: Int) : StationListPriceHistoryUiModel

    data class Decreased(val amountWon: Int) : StationListPriceHistoryUiModel

    companion object {
        fun from(delta: StationPriceDelta): StationListPriceHistoryUiModel = when (delta) {
            StationPriceDelta.Unavailable -> Unavailable
            StationPriceDelta.Unchanged -> Unchanged
            is StationPriceDelta.Increased -> Increased(delta.amountWon)
            is StationPriceDelta.Decreased -> Decreased(delta.amountWon)
        }
    }
}

internal enum class PriceDeltaTone {
    Rise,
    Fall,
    Neutral,
}
