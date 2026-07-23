package com.gasstation.data.station

import com.gasstation.core.database.DatabaseTransactionRunner
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.observability.CrashReporter
import com.gasstation.data.station.mapper.toEntity
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStationRepository @Inject constructor(
    private val stationCacheDao: StationCacheDao,
    private val stationPriceHistoryDao: StationPriceHistoryDao,
    private val watchedStationDao: WatchedStationDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val cachePolicy: StationCachePolicy,
    private val retryPolicy: StationRetryPolicy,
    private val stationEventLogger: StationEventLogger,
    private val crashReporter: CrashReporter,
    private val transactionRunner: DatabaseTransactionRunner,
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
                return@flatMapLatest flowOf(emptySearchResult())
            }

            val fetchedAt = Instant.ofEpochMilli(snapshot.fetchedAtEpochMillis)
            if (cachedStations.isEmpty()) {
                return@flatMapLatest flowOf(snapshotOnlyResult(fetchedAt))
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

    private fun emptySearchResult(): StationSearchResult = StationSearchResult(
        stations = emptyList(),
        freshness = StationFreshness.Stale,
        fetchedAt = null,
        hasCachedSnapshot = false,
    )

    private fun snapshotOnlyResult(fetchedAt: Instant): StationSearchResult = StationSearchResult(
        stations = emptyList(),
        freshness = cachePolicy.freshnessOf(fetchedAt, clock.instant()),
        fetchedAt = fetchedAt,
        hasCachedSnapshot = true,
    )

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> =
        watchedStationDao.observeWatchedStations().flatMapLatest { watchedStations ->
            if (watchedStations.isEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            val stationIds = watchedStations.map { it.stationId }.distinct()
            combine(
                stationCacheDao.observeLatestStationsByIdsAndFuelType(
                    stationIds = stationIds,
                    fuelType = query.fuelType.name,
                ),
                stationPriceHistoryDao.observeByStationIdsAndFuelType(
                    stationIds = stationIds,
                    fuelType = query.fuelType.name,
                ),
            ) { cachedStations, historyRows ->
                val latestCacheByStationId = cachedStations.associateBy { it.stationId }
                val historyRowsByStationId = historyRows.groupByStationId()
                watchedStations.mapNotNull { watchedStation ->
                    watchedStation.toWatchedSummary(
                        origin = query.origin,
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
            when (val result = remoteDataSource.fetchStations(query)) {
                is RemoteStationFetchResult.Failure -> throw StationRefreshException(
                    reason = result.reason,
                    cause = result.cause,
                )

                is RemoteStationFetchResult.Success -> result
            }
        }

        val snapshotEntities = remoteStations.stations.map { it.toEntity(cacheKey, fetchedAt) }
        val historyEntities = remoteStations.stations.map { station ->
            StationPriceHistoryEntity(
                stationId = station.stationId,
                fuelType = cacheKey.fuelType.name,
                priceWon = station.priceWon,
                fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
            )
        }

        transactionRunner.withTransaction {
            stationCacheDao.replaceSnapshot(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType.name,
                fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
                entities = snapshotEntities,
            )
            stationPriceHistoryDao.insertAll(historyEntities)
            remoteStations.stations.map { it.stationId }.distinct().forEach { stationId ->
                stationPriceHistoryDao.keepLatestTenByStationAndFuelType(
                    stationId = stationId,
                    fuelType = cacheKey.fuelType.name,
                )
            }
            stationCacheDao.pruneOlderThan(cachePolicy.pruneCutoff(fetchedAt).toEpochMilli())
        }
        stationEventLogger.logSafely(
            StationEvent.SearchRefreshed(
                radius = query.radius,
                fuelType = query.fuelType,
                sortOrder = query.sortOrder,
                stale = cachePolicy.freshnessOf(fetchedAt, clock.instant()) is StationFreshness.Stale,
            ),
        )
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
            removeWatchedStation(station.id)
        }
    }

    override suspend fun removeWatchedStation(stationId: String) {
        watchedStationDao.delete(stationId)
    }

    private companion object {
        const val DEFAULT_BUCKET_METERS = 250
    }
}
