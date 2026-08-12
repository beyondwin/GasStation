package com.gasstation.data.station

import com.gasstation.core.database.DatabaseTransactionRunner
import com.gasstation.core.database.station.StationBucketSnapshotObserver
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.observability.CrashReporter
import com.gasstation.core.observability.recordNonFatalSafely
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
import com.gasstation.domain.station.model.StationQueryCacheKey
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStationRepository @Inject internal constructor(
    private val stationCacheDao: StationCacheDao,
    private val stationBucketSnapshotObserver: StationBucketSnapshotObserver,
    private val stationPriceHistoryDao: StationPriceHistoryDao,
    private val watchedStationDao: WatchedStationDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val cachePolicy: StationCachePolicy,
    private val retryPolicy: StationRetryPolicy,
    private val stationEventLogger: StationEventLogger,
    private val crashReporter: CrashReporter,
    private val transactionRunner: DatabaseTransactionRunner,
    private val clock: Clock,
    private val freshnessTicker: StationFreshnessTicker,
    private val latestRefreshGate: LatestRefreshGate,
    private val latestWatchIntentGate: LatestWatchIntentGate,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)

        return stationBucketSnapshotObserver.observe(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
        ).flatMapLatest { bucketSnapshot ->
            val snapshot = bucketSnapshot.marker
            val cachedStations = bucketSnapshot.rows
            if (snapshot == null) {
                return@flatMapLatest flowOf(emptySearchResult())
            }

            val fetchedAt = Instant.ofEpochMilli(snapshot.fetchedAtEpochMillis)
            if (cachedStations.isEmpty()) {
                return@flatMapLatest freshnessTicker.observe(fetchedAt).map { freshness ->
                    snapshotOnlyResult(fetchedAt, freshness)
                }
            }

            val stationIds = cachedStations.map { it.stationId }.distinct()
            combine(
                freshnessTicker.observe(fetchedAt),
                watchedStationDao.observeWatchedStationIds(),
                stationPriceHistoryDao.observeByStationIdsAndFuelType(
                    stationIds = stationIds,
                    fuelType = query.fuelType.name,
                ),
            ) { freshness, watchedStationIds, historyRows ->
                cachedStations.toSearchResult(
                    query = query,
                    watchedStationIds = watchedStationIds.toSet(),
                    historyRowsByStationId = historyRows.groupByStationId(),
                    fetchedAt = fetchedAt,
                    freshness = freshness,
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

    private fun snapshotOnlyResult(fetchedAt: Instant, freshness: StationFreshness): StationSearchResult = StationSearchResult(
        stations = emptyList(),
        freshness = freshness,
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
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
        val ticket = latestRefreshGate.begin(cacheKey)
        try {
            refreshNearbyStationsInternal(query, cacheKey, ticket)
        } catch (_: RefreshSupersededException) {
            return
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: StationRefreshException) {
            throw exception
        } catch (throwable: Throwable) {
            when (
                val result = latestRefreshGate.commitIfLatest(ticket) {
                    crashReporter.recordNonFatalSafely(
                        throwable,
                        mapOf("module" to "data:station", "operation" to "refreshNearbyStations"),
                    )
                    StationRefreshException(reason = StationRefreshFailureReason.Unknown, cause = throwable)
                }
            ) {
                is LatestCommitResult.Committed -> throw result.value
                LatestCommitResult.Superseded -> return
            }
        } finally {
            withContext(NonCancellable) {
                latestRefreshGate.complete(ticket)
            }
        }
    }

    private suspend fun refreshNearbyStationsInternal(query: StationQuery, cacheKey: StationQueryCacheKey, ticket: RefreshTicket) {
        val remoteResult = retryPolicy.execute {
            ensureLatest(ticket)
            val result = try {
                remoteDataSource.fetchStations(query)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (throwable: Throwable) {
                ensureLatest(ticket)
                throw throwable
            }
            when (result) {
                is RemoteStationFetchResult.Failure -> {
                    ensureLatest(ticket)
                    throw StationRefreshException(
                        reason = result.reason,
                        cause = result.cause,
                    )
                }

                is RemoteStationFetchResult.Success -> result
            }
        }

        if (remoteResult is RetryExecutionResult.Failure) {
            latestRefreshGate.commitIfLatest(ticket) {
                remoteResult.retryReason?.let { retryReason ->
                    stationEventLogger.logSafely(
                        StationEvent.RetryAttempted(
                            originalReason = retryReason,
                            succeeded = false,
                        ),
                    )
                }
                throw remoteResult.exception
            }
            return
        }

        val success = remoteResult as RetryExecutionResult.Success
        val commitResult = latestRefreshGate.commitIfLatest(ticket) {
            val fetchedAt = clock.instant()
            val snapshotEntities = success.value.stations.map { it.toEntity(cacheKey, fetchedAt) }
            val historyEntities = success.value.stations.map { station ->
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
                success.value.stations.map { it.stationId }.distinct().forEach { stationId ->
                    stationPriceHistoryDao.keepLatestTenByStationAndFuelType(
                        stationId = stationId,
                        fuelType = cacheKey.fuelType.name,
                    )
                }
                stationCacheDao.pruneOlderThan(cachePolicy.pruneCutoff(fetchedAt).toEpochMilli())
            }
            fetchedAt
        }

        if (commitResult is LatestCommitResult.Superseded) return
        val fetchedAt = (commitResult as LatestCommitResult.Committed).value
        success.retryReason?.let { retryReason ->
            stationEventLogger.logSafely(
                StationEvent.RetryAttempted(
                    originalReason = retryReason,
                    succeeded = true,
                ),
            )
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

    private suspend fun ensureLatest(ticket: RefreshTicket) {
        if (latestRefreshGate.commitIfLatest(ticket) {} is LatestCommitResult.Superseded) {
            throw RefreshSupersededException()
        }
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult = mutateWatchState(station.id) {
        if (watched) {
            watchedStationDao.insertIfAbsent(
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
            deleteWatchedStation(station.id)
        }
    }

    override suspend fun removeWatchedStation(stationId: String): WatchMutationResult = mutateWatchState(stationId) {
        deleteWatchedStation(stationId)
    }

    private suspend fun <T> mutateWatchState(stationId: String, block: suspend () -> T): WatchMutationResult {
        val ticket = latestWatchIntentGate.begin(stationId)
        return try {
            when (latestWatchIntentGate.commitIfLatest(ticket, block)) {
                is LatestWatchCommitResult.Committed -> WatchMutationResult.Committed
                LatestWatchCommitResult.Superseded -> WatchMutationResult.Superseded
            }
        } finally {
            withContext(NonCancellable) {
                latestWatchIntentGate.complete(ticket)
            }
        }
    }

    private suspend fun deleteWatchedStation(stationId: String) {
        watchedStationDao.delete(stationId)
    }

    private companion object {
        const val DEFAULT_BUCKET_METERS = 250
    }
}

private class RefreshSupersededException : CancellationException("Refresh was superseded")
