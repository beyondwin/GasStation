package com.gasstation.feature.stationlist

data class StationListDecisionSummary(
    val count: Int,
    val lowestPriceWon: Int,
    val averagePriceWon: Int?,
    val savingsWon: Int?,
    val isLowestPriceTied: Boolean,
) {
    companion object {
        fun from(items: List<StationListItemUiModel>): StationListDecisionSummary? {
            if (items.isEmpty()) return null
            val minimum = items.minOf(StationListItemUiModel::priceWon)
            if (items.size == 1) {
                return StationListDecisionSummary(
                    count = 1,
                    lowestPriceWon = minimum,
                    averagePriceWon = null,
                    savingsWon = null,
                    isLowestPriceTied = false,
                )
            }
            val count = items.size
            val sum = items.sumOf { it.priceWon.toLong() }
            val average = ((sum + count / 2L) / count).toInt()
            return StationListDecisionSummary(
                count = count,
                lowestPriceWon = minimum,
                averagePriceWon = average,
                savingsWon = average - minimum,
                isLowestPriceTied = items.count { it.priceWon == minimum } > 1,
            )
        }
    }
}
