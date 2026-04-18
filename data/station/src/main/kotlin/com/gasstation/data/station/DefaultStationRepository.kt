package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.data.station.mapper.toDomainStation
import com.gasstation.data.station.mapper.toEntity
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import java.time.Clock
import java.time.Instant
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStationRepository @Inject constructor(
    private val stationCacheDao: StationCacheDao,
    private val stationPriceHistoryDao: StationPriceHistoryDao,
    private val watchedStationDao: WatchedStationDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
    private val cachePolicy: StationCachePolicy,
    private val clock: Clock,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)

        return stationCacheDao.observeStations(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
        ).flatMapLatest { cachedStations ->
            if (cachedStations.isEmpty()) {
                return@flatMapLatest flowOf(
                    StationSearchResult(
                        stations = emptyList(),
                        freshness = StationFreshness.Stale,
                        fetchedAt = null,
                    ),
                )
            }

            val stationIds = cachedStations.map { it.stationId }.distinct()
            combine(
                watchedStationDao.observeWatchedStationIds(),
                stationPriceHistoryDao.observeByStationIdsAndFuelType(
                    stationIds = stationIds,
                    fuelType = query.fuelType.name,
                ),
            ) { watchedStationIds, historyRows ->
                cachedStations.toSearchResult(
                    query = query,
                    watchedStationIds = watchedStationIds.toSet(),
                    historyRowsByStationId = historyRows.groupByStationId(),
                    cachePolicy = cachePolicy,
                    now = clock.instant(),
                )
            }
        }
    }

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> =
        watchedStationDao.observeWatchedStations().flatMapLatest { watchedStations ->
            if (watchedStations.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            val stationIds = watchedStations.map { it.stationId }.distinct()
            combine(
                stationCacheDao.observeLatestStationsByIds(stationIds),
                stationPriceHistoryDao.observeByStationIds(stationIds),
            ) { cachedStations, historyRows ->
                val latestCacheByStationId = cachedStations.latestByStationId()
                val historyRowsByStationId = historyRows.groupByStationId()
                watchedStations.mapNotNull { watchedStation ->
                    watchedStation.toWatchedSummary(
                        origin = origin,
                        cachedStation = latestCacheByStationId[watchedStation.stationId],
                        history = historyRowsByStationId[watchedStation.stationId].orEmpty(),
                    )
                }
            }
        }

    override suspend fun refreshNearbyStations(query: StationQuery) {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
        val fetchedAt = clock.instant()
        val remoteStations = if (seedRemoteDataSource.isPresent) {
            seedRemoteDataSource.get().fetchStations(query)
        } else {
            remoteDataSource.fetchStations(query)
        }
        when (remoteStations) {
            is RemoteStationFetchResult.Failure -> {
                throw StationRefreshException()
            }
            is RemoteStationFetchResult.Success -> {
                val snapshotEntities = remoteStations.stations.map { it.toEntity(cacheKey, fetchedAt) }
                stationCacheDao.replaceSnapshot(
                    latitudeBucket = cacheKey.latitudeBucket,
                    longitudeBucket = cacheKey.longitudeBucket,
                    radiusMeters = cacheKey.radiusMeters,
                    fuelType = cacheKey.fuelType.name,
                    entities = snapshotEntities,
                )

                val historyEntities = remoteStations.stations.map { station ->
                    StationPriceHistoryEntity(
                        stationId = station.stationId,
                        fuelType = cacheKey.fuelType.name,
                        priceWon = station.priceWon,
                        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
                    )
                }
                stationPriceHistoryDao.insertAll(historyEntities)
                remoteStations.stations.forEach { station ->
                    stationPriceHistoryDao.keepLatestTenByStationAndFuelType(
                        stationId = station.stationId,
                        fuelType = cacheKey.fuelType.name,
                    )
                }
            }
        }
    }

    override suspend fun updateWatchState(
        station: Station,
        watched: Boolean,
    ) {
        if (watched) {
            watchedStationDao.upsert(
                WatchedStationEntity(
                    stationId = station.id,
                    name = station.name,
                    brandCode = station.brand.name,
                    latitude = station.coordinates.latitude,
                    longitude = station.coordinates.longitude,
                    watchedAtEpochMillis = clock.instant().toEpochMilli(),
                ),
            )
        } else {
            watchedStationDao.delete(station.id)
        }
    }

    private fun List<StationCacheEntity>.toSearchResult(
        query: StationQuery,
        watchedStationIds: Set<String>,
        historyRowsByStationId: Map<String, List<StationPriceHistoryEntity>>,
        cachePolicy: StationCachePolicy,
        now: Instant,
    ): StationSearchResult {
        val fetchedAt = maxOfOrNull { it.fetchedAtEpochMillis }?.let(Instant::ofEpochMilli)
        val stations = map { cacheRow ->
            val station = cacheRow.toDomainStation(query.coordinates)
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
            freshness = fetchedAt?.let { cachePolicy.freshnessOf(it, now) } ?: StationFreshness.Stale,
            fetchedAt = fetchedAt,
        )
    }

    private fun WatchedStationEntity.toWatchedSummary(
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
            latestPrice != null -> Station(
                id = stationId,
                name = name,
                brand = brandCode.toBrand(),
                price = MoneyWon(latestPrice.priceWon),
                distance = DistanceMeters(distanceBetween(origin, Coordinates(latitude, longitude))),
                coordinates = Coordinates(latitude = latitude, longitude = longitude),
            )
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

    private fun Map<String, List<StationPriceHistoryEntity>>.previousPriceFor(cacheRow: StationCacheEntity): Int? =
        get(cacheRow.stationId)
            .orEmpty()
            .firstOrNull { it.fetchedAtEpochMillis < cacheRow.fetchedAtEpochMillis }
            ?.priceWon

    private fun List<StationPriceHistoryEntity>.groupByStationId(): Map<String, List<StationPriceHistoryEntity>> =
        groupBy { it.stationId }.mapValues { (_, rows) ->
            rows.sortedByDescending { it.fetchedAtEpochMillis }
        }

    private fun List<StationPriceHistoryEntity>.historyForWatchlistContext(
        cachedFuelType: String?,
    ): List<StationPriceHistoryEntity> {
        if (isEmpty()) return emptyList()

        val fuelType = cachedFuelType
            ?: maxBy { it.fetchedAtEpochMillis }.fuelType

        return filter { it.fuelType == fuelType }
            .sortedByDescending { it.fetchedAtEpochMillis }
    }

    private fun List<StationCacheEntity>.latestByStationId(): Map<String, StationCacheEntity> =
        sortedWith(
            compareBy<StationCacheEntity> { it.stationId }
                .thenByDescending { it.fetchedAtEpochMillis }
                .thenBy { it.fuelType }
                .thenBy { it.radiusMeters }
                .thenBy { it.latitudeBucket }
                .thenBy { it.longitudeBucket },
        ).groupBy { it.stationId }.mapValues { (_, rows) ->
            rows.first()
        }

    private fun historyRowsBefore(
        fetchedAtEpochMillis: Long,
        history: List<StationPriceHistoryEntity>,
    ): List<StationPriceHistoryEntity> = history.filter { it.fetchedAtEpochMillis < fetchedAtEpochMillis }

    private fun List<StationListEntry>.sortedFor(sortOrder: SortOrder): List<StationListEntry> = when (sortOrder) {
        SortOrder.DISTANCE -> sortedBy { it.station.distance.value }
        SortOrder.PRICE -> sortedBy { it.station.price.value }
    }

    private fun String.toBrand(): Brand = Brand.entries.firstOrNull { it.name == this } ?: Brand.ETC

    private companion object {
        const val DEFAULT_BUCKET_METERS = 250
    }
}

private fun distanceBetween(
    origin: Coordinates,
    destination: Coordinates,
): Int {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(destination.latitude - origin.latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - origin.longitude)
    val originLatitudeRadians = Math.toRadians(origin.latitude)
    val destinationLatitudeRadians = Math.toRadians(destination.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitudeRadians) *
        cos(destinationLatitudeRadians) *
        sin(longitudeDelta / 2).let { it * it }
    val centralAngle = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return (earthRadiusMeters * centralAngle).roundToInt()
}

class StationRefreshException : IllegalStateException("Failed to refresh nearby stations")
