package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.model.distanceTo
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Instant

internal fun WatchedStationEntity.toWatchedSummary(
    origin: Coordinates,
    cachedStation: StationCacheEntity?,
    history: List<StationPriceHistoryEntity>,
): WatchedStationSummary? {
    val savedCoordinates = Coordinates.ofOrNull(latitude, longitude) ?: return null
    val validCachePrice = cachedStation?.priceWon?.let(MoneyWon::ofOrNull)
    val validCacheCoordinates = cachedStation?.let {
        Coordinates.ofOrNull(it.latitude, it.longitude)
    }
    val historyRows = history.sortedByDescending { it.fetchedAtEpochMillis }
    val historyPrice = historyRows.firstOrNull()?.priceWon?.let(MoneyWon::ofOrNull)
    val price = validCachePrice ?: historyPrice
    val coordinates = validCacheCoordinates ?: savedCoordinates
    val useCachedIdentity = validCachePrice != null && validCacheCoordinates != null

    return WatchedStationSummary(
        id = stationId,
        name = cachedStation?.name?.takeIf { useCachedIdentity } ?: name,
        brand = cachedStation?.brandCode?.takeIf { useCachedIdentity }?.let(Brand::fromCode)
            ?: Brand.fromCode(brandCode),
        price = price,
        distance = origin.distanceTo(coordinates),
        coordinates = coordinates,
        priceDelta = resolvePriceDelta(
            cachedStation = cachedStation?.takeIf { validCachePrice != null },
            history = historyRows,
            currentPrice = price,
        ),
        lastSeenAt = cachedStation?.fetchedAtEpochMillis?.takeIf { validCachePrice != null }?.let(Instant::ofEpochMilli)
            ?: historyRows.firstOrNull()?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
    )
}

private fun resolvePriceDelta(
    cachedStation: StationCacheEntity?,
    history: List<StationPriceHistoryEntity>,
    currentPrice: MoneyWon?,
): StationPriceDelta = when {
    cachedStation != null -> StationPriceDelta.from(
        previousPriceWon = historyRowsBefore(
            fetchedAtEpochMillis = cachedStation.fetchedAtEpochMillis,
            history = history,
        ).firstOrNull()?.priceWon?.let(MoneyWon::ofOrNull)?.value,
        currentPriceWon = cachedStation.priceWon,
    )

    currentPrice != null -> StationPriceDelta.from(
        previousPriceWon = history.drop(1).firstOrNull()?.priceWon?.let(MoneyWon::ofOrNull)?.value,
        currentPriceWon = currentPrice.value,
    )

    else -> StationPriceDelta.Unavailable
}

private fun historyRowsBefore(fetchedAtEpochMillis: Long, history: List<StationPriceHistoryEntity>): List<StationPriceHistoryEntity> =
    history.filter { it.fetchedAtEpochMillis < fetchedAtEpochMillis }
