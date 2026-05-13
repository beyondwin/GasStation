package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.model.distanceTo
import com.gasstation.data.station.mapper.toDomainStation
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Instant

internal fun WatchedStationEntity.toWatchedSummary(
    origin: Coordinates,
    cachedStation: StationCacheEntity?,
    history: List<StationPriceHistoryEntity>,
): WatchedStationSummary? {
    val cachedSnapshot = cachedStation?.toDomainStation(origin)
    val historyForContext = history.historyForWatchlistContext(cachedStation?.fuelType)
    val latestPrice = historyForContext.firstOrNull()
    val previousPrice = historyForContext.drop(1).firstOrNull()
    val station = when {
        cachedSnapshot != null -> cachedSnapshot
        latestPrice != null -> {
            val stationCoordinates = Coordinates(latitude, longitude)
            Station(
                id = stationId,
                name = name,
                brand = Brand.fromCode(brandCode),
                price = MoneyWon(latestPrice.priceWon),
                distance = origin.distanceTo(stationCoordinates),
                coordinates = stationCoordinates,
            )
        }
        else -> return null
    }
    val priceDelta = when {
        cachedStation != null -> StationPriceDelta.from(
            previousPriceWon = historyRowsBefore(
                fetchedAtEpochMillis = cachedStation.fetchedAtEpochMillis,
                history = historyForContext,
            ).firstOrNull()?.priceWon,
            currentPriceWon = cachedStation.priceWon,
        )
        latestPrice != null -> StationPriceDelta.from(
            previousPriceWon = previousPrice?.priceWon,
            currentPriceWon = latestPrice.priceWon,
        )
        else -> StationPriceDelta.Unavailable
    }

    return WatchedStationSummary(
        station = station,
        priceDelta = priceDelta,
        lastSeenAt = cachedStation?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)
            ?: latestPrice?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
    )
}

private fun List<StationPriceHistoryEntity>.historyForWatchlistContext(cachedFuelType: String?): List<StationPriceHistoryEntity> {
    if (isEmpty()) return emptyList()

    val fuelType = cachedFuelType
        ?: maxBy { it.fetchedAtEpochMillis }.fuelType

    return filter { it.fuelType == fuelType }
        .sortedByDescending { it.fetchedAtEpochMillis }
}

private fun historyRowsBefore(fetchedAtEpochMillis: Long, history: List<StationPriceHistoryEntity>): List<StationPriceHistoryEntity> =
    history.filter { it.fetchedAtEpochMillis < fetchedAtEpochMillis }
