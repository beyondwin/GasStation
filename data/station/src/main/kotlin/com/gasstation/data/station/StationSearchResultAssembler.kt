package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.model.SortOrder
import com.gasstation.data.station.mapper.toDomainStation
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import java.time.Instant

internal fun List<StationCacheEntity>.toSearchResult(
    query: StationQuery,
    watchedStationIds: Set<String>,
    historyRowsByStationId: Map<String, List<StationPriceHistoryEntity>>,
    fetchedAt: Instant,
    cachePolicy: StationCachePolicy,
    now: Instant,
): StationSearchResult {
    val stations = mapNotNull { cacheRow ->
        val station = cacheRow.toDomainStation(query.coordinates) ?: return@mapNotNull null
        StationListEntry(
            station = station,
            priceDelta = StationPriceDelta.from(
                previousPriceWon = historyRowsByStationId.previousPriceFor(cacheRow),
                currentPriceWon = cacheRow.priceWon,
            ),
            isWatched = cacheRow.stationId in watchedStationIds,
            lastSeenAt = Instant.ofEpochMilli(cacheRow.fetchedAtEpochMillis),
        )
    }
        .filter { query.brandFilter.matches(it.station.brand) }
        .sortedFor(query.sortOrder)

    return StationSearchResult(
        stations = stations,
        freshness = cachePolicy.freshnessOf(fetchedAt, now),
        fetchedAt = fetchedAt,
        hasCachedSnapshot = true,
    )
}

private fun Map<String, List<StationPriceHistoryEntity>>.previousPriceFor(cacheRow: StationCacheEntity): Int? = get(cacheRow.stationId)
    .orEmpty()
    .firstOrNull { it.fetchedAtEpochMillis < cacheRow.fetchedAtEpochMillis }
    ?.priceWon

internal fun List<StationPriceHistoryEntity>.groupByStationId(): Map<String, List<StationPriceHistoryEntity>> =
    groupBy { it.stationId }.mapValues { (_, rows) ->
        rows.sortedByDescending { it.fetchedAtEpochMillis }
    }

private fun List<StationListEntry>.sortedFor(sortOrder: SortOrder): List<StationListEntry> = when (sortOrder) {
    SortOrder.DISTANCE -> sortedBy { it.station.distance.value }
    SortOrder.PRICE -> sortedBy { it.station.price.value }
}
