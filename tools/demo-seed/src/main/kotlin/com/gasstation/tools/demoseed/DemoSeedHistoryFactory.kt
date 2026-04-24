package com.gasstation.tools.demoseed

import com.gasstation.core.model.FuelType
import kotlin.math.absoluteValue
import kotlin.math.max

data class DemoSeedHistoryEntry(
    val priceWon: Int,
    val fetchedAtEpochMillis: Long,
)

object DemoSeedHistoryFactory {
    private const val DayMillis = 24L * 60L * 60L * 1_000L

    fun createEntries(
        stationId: String,
        fuelType: FuelType,
        latestPriceWon: Int,
        generatedAtEpochMillis: Long,
    ): List<DemoSeedHistoryEntry> {
        val seed = "${stationId}:${fuelType.name}".hashCode().absoluteValue
        val offsetOneDay = (seed % 41) - 20
        val offsetTwoDays = (seed % 71) - 35

        return listOf(
            DemoSeedHistoryEntry(
                priceWon = max(1, latestPriceWon + offsetTwoDays),
                fetchedAtEpochMillis = generatedAtEpochMillis - (2 * DayMillis),
            ),
            DemoSeedHistoryEntry(
                priceWon = max(1, latestPriceWon + offsetOneDay),
                fetchedAtEpochMillis = generatedAtEpochMillis - DayMillis,
            ),
            DemoSeedHistoryEntry(
                priceWon = latestPriceWon,
                fetchedAtEpochMillis = generatedAtEpochMillis,
            ),
        )
    }
}
