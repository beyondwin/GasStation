package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.model.SortOrder
import com.gasstation.core.model.distanceTo
import com.gasstation.core.observability.CrashReporter
import com.gasstation.data.station.mapper.toDomainStation
import com.gasstation.data.station.mapper.toEntity
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.Optional
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStationRepository @Inject constructor(
    private val stationCacheDao: StationCacheDao,
    private val stationPriceHistoryDao: StationPriceHistoryDao,
    private val watchedStationDao: WatchedStationDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
    private val cachePolicy: StationCachePolicy,
    private val retryPolicy: StationRetryPolicy,
    private val stationEventLogger: StationEventLogger,
    private val crashReporter: CrashReporter,
    private val clock: Clock,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)

        return combine(
            stationCacheDao.observeSnapshot(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType.name,
            ),
            stationCacheDao.observeStations(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType.name,
            ),
        ) { snapshot, cachedStations ->
            snapshot to cachedStations
        }.flatMapLatest { (snapshot, cachedStations) ->
            if (snapshot == null) {
                return@flatMapLatest flowOf(
                    StationSearchResult(
                        stations = emptyList(),
                        freshness = StationFreshness.Stale,
                        fetchedAt = null,
                        hasCachedSnapshot = false,
                    ),
                )
            }

            val fetchedAt = Instant.ofEpochMilli(snapshot.fetchedAtEpochMillis)
            if (cachedStations.isEmpty()) {
                return@flatMapLatest flowOf(
                    StationSearchResult(
                        stations = emptyList(),
                        freshness = cachePolicy.freshnessOf(fetchedAt, clock.instant()),
                        fetchedAt = fetchedAt,
                        hasCachedSnapshot = true,
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
                    fetchedAt = fetchedAt,
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
                val latestCacheByStationId = cachedStations.associateBy { it.stationId }
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
        try {
            refreshNearbyStationsInternal(query)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: StationRefreshException) {
            throw exception
        } catch (throwable: Throwable) {
            crashReporter.recordNonFatal(
                throwable,
                mapOf("module" to "data:station", "operation" to "refreshNearbyStations"),
            )
            throw StationRefreshException(reason = StationRefreshFailureReason.Unknown, cause = throwable)
        }
    }

    private suspend fun refreshNearbyStationsInternal(query: StationQuery) {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
        val fetchedAt = clock.instant()
        val remoteStations = retryPolicy.withRetry {
            when (val result = fetchRemoteStations(query)) {
                is RemoteStationFetchResult.Failure -> throw StationRefreshException(
                    reason = result.reason,
                    cause = result.cause,
                )
                is RemoteStationFetchResult.Success -> result
            }
        }

        val snapshotEntities = remoteStations.stations.map { it.toEntity(cacheKey, fetchedAt) }
        stationCacheDao.replaceSnapshot(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
            fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
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
        stationCacheDao.pruneOlderThan(cachePolicy.pruneCutoff(fetchedAt).toEpochMilli())
        stationEventLogger.logSafely(
            StationEvent.SearchRefreshed(
                radius = query.radius,
                fuelType = query.fuelType,
                sortOrder = query.sortOrder,
                stale = cachePolicy.freshnessOf(fetchedAt, clock.instant()) is StationFreshness.Stale,
            ),
        )
    }

    private suspend fun fetchRemoteStations(query: StationQuery): RemoteStationFetchResult = if (seedRemoteDataSource.isPresent) {
        seedRemoteDataSource.get().fetchStations(query)
    } else {
        remoteDataSource.fetchStations(query)
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) {
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
        fetchedAt: Instant,
        cachePolicy: StationCachePolicy,
        now: Instant,
    ): StationSearchResult {
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
            freshness = cachePolicy.freshnessOf(fetchedAt, now),
            fetchedAt = fetchedAt,
            hasCachedSnapshot = true,
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

    private fun Map<String, List<StationPriceHistoryEntity>>.previousPriceFor(cacheRow: StationCacheEntity): Int? = get(cacheRow.stationId)
        .orEmpty()
        .firstOrNull { it.fetchedAtEpochMillis < cacheRow.fetchedAtEpochMillis }
        ?.priceWon

    private fun List<StationPriceHistoryEntity>.groupByStationId(): Map<String, List<StationPriceHistoryEntity>> =
        groupBy { it.stationId }.mapValues { (_, rows) ->
            rows.sortedByDescending { it.fetchedAtEpochMillis }
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

    private fun List<StationListEntry>.sortedFor(sortOrder: SortOrder): List<StationListEntry> = when (sortOrder) {
        SortOrder.DISTANCE -> sortedBy { it.station.distance.value }
        SortOrder.PRICE -> sortedBy { it.station.price.value }
    }

    private companion object {
        const val DEFAULT_BUCKET_METERS = 250
    }
}
