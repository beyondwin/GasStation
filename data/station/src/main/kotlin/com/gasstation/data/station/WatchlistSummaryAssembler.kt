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
    val validCachedStation = cachedStation?.takeIf { cachedSnapshot != null }
    val historyForContext = history.historyForWatchlistContext(cachedStation?.fuelType)
    val station = resolveStation(origin, cachedSnapshot, historyForContext) ?: return null
    val priceDelta = resolvePriceDelta(validCachedStation, historyForContext)
    return WatchedStationSummary(
        station = station,
        priceDelta = priceDelta,
        lastSeenAt = resolveLastSeenAt(validCachedStation, historyForContext),
    )
}

private fun WatchedStationEntity.resolveStation(
    origin: Coordinates,
    cachedSnapshot: Station?,
    historyForContext: List<StationPriceHistoryEntity>,
): Station? {
    val latestPrice = historyForContext.firstOrNull()
    return when {
        cachedSnapshot != null -> cachedSnapshot

        latestPrice != null -> {
            val stationCoordinates = Coordinates.ofOrNull(latitude, longitude) ?: return null
            val price = MoneyWon.ofOrNull(latestPrice.priceWon) ?: return null
            Station(
                id = stationId,
                name = name,
                brand = Brand.fromCode(brandCode),
                price = price,
                distance = origin.distanceTo(stationCoordinates),
                coordinates = stationCoordinates,
            )
        }

        else -> null
    }
}

private fun resolvePriceDelta(cachedStation: StationCacheEntity?, historyForContext: List<StationPriceHistoryEntity>): StationPriceDelta =
    when {
        cachedStation != null -> StationPriceDelta.from(
            previousPriceWon = historyRowsBefore(
                fetchedAtEpochMillis = cachedStation.fetchedAtEpochMillis,
                history = historyForContext,
            ).firstOrNull()?.priceWon,
            currentPriceWon = cachedStation.priceWon,
        )

        historyForContext.isNotEmpty() -> StationPriceDelta.from(
            previousPriceWon = historyForContext.drop(1).firstOrNull()?.priceWon,
            currentPriceWon = historyForContext.first().priceWon,
        )

        else -> StationPriceDelta.Unavailable
    }

private fun resolveLastSeenAt(cachedStation: StationCacheEntity?, historyForContext: List<StationPriceHistoryEntity>): Instant? =
    cachedStation?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)
        ?: historyForContext.firstOrNull()?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)

private fun List<StationPriceHistoryEntity>.historyForWatchlistContext(cachedFuelType: String?): List<StationPriceHistoryEntity> {
    if (isEmpty()) return emptyList()

    val fuelType = cachedFuelType
        ?: maxBy { it.fetchedAtEpochMillis }.fuelType

    return filter { it.fuelType == fuelType }
        .sortedByDescending { it.fetchedAtEpochMillis }
}

private fun historyRowsBefore(fetchedAtEpochMillis: Long, history: List<StationPriceHistoryEntity>): List<StationPriceHistoryEntity> =
    history.filter { it.fetchedAtEpochMillis < fetchedAtEpochMillis }
