package com.gasstation.data.station

import com.gasstation.core.database.DatabaseTransactionRunner
import com.gasstation.core.database.station.StationBucketSnapshot
import com.gasstation.core.database.station.StationBucketSnapshotObserver
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationCacheSnapshotEntity
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.core.model.distanceTo
import com.gasstation.core.observability.CrashReporter
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultStationRepositoryTest {
    private val now = Instant.parse("2026-04-18T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `refreshNearbyStations replaces the cached snapshot for the bucket`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val otherKey = cacheKey.copy(latitudeBucket = cacheKey.latitudeBucket + 1)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "stale-station",
                fetchedAt = now.minusSeconds(600),
            ),
            stationEntity(
                cacheKey = otherKey,
                stationId = "other-bucket",
                fetchedAt = now.minusSeconds(600),
            ),
        )
        val repository = repository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(
                    listOf(
                        RemoteStation(
                            stationId = "station-1",
                            name = "Gangnam First",
                            brandCode = "GSC",
                            priceWon = 1_689,
                            coordinates = Coordinates(37.499095, 127.027610),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        val refreshedStations = stationCacheDao.snapshotFor(cacheKey)
        val unrelatedStations = stationCacheDao.snapshotFor(otherKey)

        assertEquals(1, stationCacheDao.replaceSnapshotCalls.size)
        assertEquals(listOf("station-1"), refreshedStations.map { it.stationId })
        assertEquals(listOf("other-bucket"), unrelatedStations.map { it.stationId })
        assertEquals(now.toEpochMilli(), refreshedStations.single().fetchedAtEpochMillis)
    }

    @Test
    fun `refreshNearbyStations persists price history rows and trims old entries`() = runBlocking {
        val query = stationQuery()
        val stationPriceHistoryDao = RecordingStationPriceHistoryDao(
            history = (1..10).map { offset ->
                history(
                    stationId = "station-1",
                    priceWon = 1_700 + offset,
                    fetchedAt = now.minusSeconds(offset.toLong() * 60),
                )
            },
        )
        val repository = repository(
            stationPriceHistoryDao = stationPriceHistoryDao,
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(
                    listOf(
                        RemoteStation(
                            stationId = "station-1",
                            name = "Gangnam First",
                            brandCode = "GSC",
                            priceWon = 1_680,
                            coordinates = Coordinates(37.499095, 127.027610),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        assertEquals(1, stationPriceHistoryDao.insertAllCalls.size)
        assertEquals(listOf("station-1" to query.fuelType.name), stationPriceHistoryDao.keepLatestTenCalls)
        assertEquals(10, stationPriceHistoryDao.entriesFor("station-1", fuelType = query.fuelType.name).size)
        assertEquals(
            now.toEpochMilli(),
            stationPriceHistoryDao.entriesFor("station-1", fuelType = query.fuelType.name).first().fetchedAtEpochMillis,
        )
        assertTrue(
            stationPriceHistoryDao.entriesFor("station-1", fuelType = query.fuelType.name)
                .none { it.fetchedAtEpochMillis == now.minusSeconds(10 * 60L).toEpochMilli() },
        )
    }

    @Test
    fun `refreshNearbyStations runs all writes inside a single transaction with deduplicated prune calls`() = runBlocking {
        val query = stationQuery()
        val stationPriceHistoryDao = RecordingStationPriceHistoryDao()
        val transactionRunner = ImmediateDatabaseTransactionRunner()
        val repository = repository(
            stationPriceHistoryDao = stationPriceHistoryDao,
            transactionRunner = transactionRunner,
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(
                    listOf(
                        RemoteStation(
                            stationId = "station-1",
                            name = "Gangnam First",
                            brandCode = "GSC",
                            priceWon = 1_680,
                            coordinates = Coordinates(37.499095, 127.027610),
                        ),
                        RemoteStation(
                            stationId = "station-1",
                            name = "Gangnam First Duplicate",
                            brandCode = "GSC",
                            priceWon = 1_690,
                            coordinates = Coordinates(37.499095, 127.027610),
                        ),
                        RemoteStation(
                            stationId = "station-2",
                            name = "Gangnam Second",
                            brandCode = "SKE",
                            priceWon = 1_700,
                            coordinates = Coordinates(37.500095, 127.027610),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        assertEquals(1, transactionRunner.invocations)
        assertEquals(
            listOf("station-1" to query.fuelType.name, "station-2" to query.fuelType.name),
            stationPriceHistoryDao.keepLatestTenCalls,
        )
    }

    @Test
    fun `refreshNearbyStations prunes cache rows after successful persistence`() = runBlocking {
        val query = stationQuery()
        val stationCacheDao = RecordingStationCacheDao()
        val repository = repository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(
                    listOf(
                        RemoteStation(
                            stationId = "station-1",
                            name = "Gangnam First",
                            brandCode = "GSC",
                            priceWon = 1_689,
                            coordinates = Coordinates(37.499095, 127.027610),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        assertEquals(listOf(Instant.parse("2026-04-11T03:00:00Z").toEpochMilli()), stationCacheDao.pruneCutoffCalls)
    }

    @Test
    fun `refreshNearbyStations logs search refreshed after successful persistence`() = runBlocking {
        val query = stationQuery(sortOrder = SortOrder.PRICE)
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val repository = repository(
            analytics = analytics,
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(emptyList()),
            ),
        )

        repository.refreshNearbyStations(query)

        assertEquals(
            listOf(
                StationEvent.SearchRefreshed(
                    radius = SearchRadius.KM_3,
                    fuelType = FuelType.GASOLINE,
                    sortOrder = SortOrder.PRICE,
                    stale = false,
                ),
            ),
            analytics.events,
        )
    }

    @Test
    fun `refreshNearbyStations completes after persistence when search refreshed logging fails`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        val repository = repository(
            stationCacheDao = stationCacheDao,
            analytics = RepositoryDoubles.ThrowingStationEventLogger(),
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(
                    listOf(
                        RemoteStation(
                            stationId = "station-1",
                            name = "Gangnam First",
                            brandCode = "GSC",
                            priceWon = 1_689,
                            coordinates = Coordinates(37.499095, 127.027610),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        assertEquals(listOf("station-1"), stationCacheDao.snapshotFor(cacheKey).map { it.stationId })
    }

    @Test
    fun `observeNearbyStations filters by brand and sorts by price client side`() = runBlocking {
        val query = stationQuery(
            brandFilter = BrandFilter.GSC,
            sortOrder = SortOrder.PRICE,
        )
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "cheap-gsc",
                brandCode = "GSC",
                priceWon = 1_610,
                latitude = 37.499095,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "expensive-gsc",
                brandCode = "GSC",
                priceWon = 1_710,
                latitude = 37.498295,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "filtered-out",
                brandCode = "SKE",
                priceWon = 1_400,
                latitude = 37.498195,
            ),
        )
        val repository = repository(stationCacheDao = stationCacheDao)

        val result = repository.observeNearbyStations(query).first()

        assertEquals(listOf("cheap-gsc", "expensive-gsc"), result.stations.map { it.station.id })
        assertEquals(StationFreshness.Fresh, result.freshness)
        assertEquals(now, result.fetchedAt)
    }

    @Test
    fun `observeNearbyStations reads the injected atomic bucket snapshot`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val atomicSnapshot = StationBucketSnapshot(
            marker = com.gasstation.core.database.station.StationCacheSnapshotEntity(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType.name,
                fetchedAtEpochMillis = now.toEpochMilli(),
            ),
            rows = listOf(
                stationEntity(
                    cacheKey = cacheKey,
                    stationId = "atomic-station",
                    fetchedAt = now,
                ),
            ),
        )
        val repository = repository(
            stationCacheDao = RecordingStationCacheDao(),
            stationBucketSnapshotObserver = StationBucketSnapshotObserver { _, _, _, _ ->
                flowOf(atomicSnapshot)
            },
        )

        val result = repository.observeNearbyStations(query).first()

        assertEquals(listOf("atomic-station"), result.stations.map { it.station.id })
        assertTrue(result.hasCachedSnapshot)
        assertEquals(now, result.fetchedAt)
    }

    @Test
    fun `observeNearbyStations enriches snapshot with price delta and watched metadata`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "station-1",
                priceWon = 1_680,
                fetchedAt = now,
            ),
        )
        val repository = repository(
            stationCacheDao = stationCacheDao,
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-1",
                        priceWon = 1_710,
                        fetchedAt = now.minusSeconds(300),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-1",
                        watchedAt = now.minusSeconds(60),
                    ),
                ),
            ),
        )

        val result = repository.observeNearbyStations(query).first()
        val entry = result.stations.single()

        assertEquals(StationPriceDelta.Decreased(30), entry.priceDelta)
        assertEquals(true, entry.isWatched)
        assertEquals(now, entry.lastSeenAt)
    }

    @Test
    fun `observeNearbyStations uses history from the current query fuel type only`() = runBlocking {
        val query = stationQuery(fuelType = FuelType.GASOLINE)
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "station-1",
                priceWon = 1_680,
                fetchedAt = now,
            ),
        )
        val repository = repository(
            stationCacheDao = stationCacheDao,
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-1",
                        fuelType = "DIESEL",
                        priceWon = 1_520,
                        fetchedAt = now.minusSeconds(120),
                    ),
                    history(
                        stationId = "station-1",
                        fuelType = "GASOLINE",
                        priceWon = 1_710,
                        fetchedAt = now.minusSeconds(300),
                    ),
                ),
            ),
        )

        val entry = repository.observeNearbyStations(query).first().stations.single()

        assertEquals(StationPriceDelta.Decreased(30), entry.priceDelta)
    }

    @Test
    fun `observeNearbyStations recalculates distance from the current query origin`() = runBlocking {
        val baseQuery = stationQuery(sortOrder = SortOrder.DISTANCE)
        val shiftedQuery = baseQuery.copy(
            coordinates = Coordinates(
                latitude = baseQuery.coordinates.latitude + 0.0007,
                longitude = baseQuery.coordinates.longitude,
            ),
        )
        val cacheKey = baseQuery.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "cheap",
                brandCode = "GSC",
                priceWon = 1_500,
                latitude = baseQuery.coordinates.latitude + 0.0010,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "mid",
                brandCode = "GSC",
                priceWon = 1_600,
                latitude = baseQuery.coordinates.latitude + 0.0002,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "other-brand",
                brandCode = "SKE",
                priceWon = 1_450,
                latitude = baseQuery.coordinates.latitude + 0.0001,
            ),
        )
        val repository = repository(stationCacheDao = stationCacheDao)

        val baseResult = repository.observeNearbyStations(baseQuery).first()
        val shiftedResult = repository.observeNearbyStations(shiftedQuery).first()

        assertEquals(listOf("other-brand", "mid", "cheap"), baseResult.stations.map { it.station.id })
        assertEquals(listOf("cheap", "mid", "other-brand"), shiftedResult.stations.map { it.station.id })
        assertEquals(
            shiftedQuery.coordinates.distanceTo(
                Coordinates(
                    latitude = shiftedQuery.coordinates.latitude + 0.0003,
                    longitude = shiftedQuery.coordinates.longitude,
                ),
            ).value,
            shiftedResult.stations.first().station.distance.value,
        )
        assertTrue(shiftedResult.stations.first().station.distance.value < baseResult.stations.last().station.distance.value)
    }

    @Test
    fun `refreshNearbyStations preserves existing snapshot when remote fetch fails`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "cached-station",
                brandCode = "GSC",
                priceWon = 1_650,
                fetchedAt = now.minusSeconds(180),
            ),
        )
        val repository = repository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(
                RemoteStationFetchResult.Failure(StationRefreshFailureReason.Timeout),
            ),
        )

        val error = assertThrows(StationRefreshException::class.java) {
            runBlocking {
                repository.refreshNearbyStations(query)
            }
        }

        val cachedStations = stationCacheDao.snapshotFor(cacheKey)

        assertEquals(StationRefreshFailureReason.Timeout, error.reason)
        assertEquals(null, error.cause)
        assertEquals(0, stationCacheDao.replaceSnapshotCalls.size)
        assertEquals(listOf("cached-station"), cachedStations.map { it.stationId })
        assertEquals(now.minusSeconds(180).toEpochMilli(), cachedStations.single().fetchedAtEpochMillis)
    }

    @Test
    fun `refresh retries network failure once and stores successful retry result`() = runTest {
        val query = stationQuery()
        val repository = repository(
            remoteDataSource = QueueStationRemoteDataSource(
                ArrayDeque(
                    listOf(
                        RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network),
                        RemoteStationFetchResult.Success(
                            listOf(
                                RemoteStation(
                                    stationId = "station-1",
                                    name = "Retry Station",
                                    brandCode = "GSC",
                                    priceWon = 1_699,
                                    coordinates = Coordinates(37.498095, 127.027610),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        val result = repository.observeNearbyStations(query).first()

        assertEquals(listOf("station-1"), result.stations.map { it.station.id })
        assertTrue(result.hasCachedSnapshot)
    }

    @Test
    fun `observeNearbyStations exposes cached empty snapshot after empty refresh`() = runBlocking {
        val query = stationQuery()
        val repository = repository(
            remoteDataSource = FakeStationRemoteDataSource(
                RemoteStationFetchResult.Success(emptyList()),
            ),
        )

        repository.refreshNearbyStations(query)

        val result = repository.observeNearbyStations(query).first()

        assertTrue(result.hasCachedSnapshot)
        assertEquals(emptyList<Any>(), result.stations)
        assertEquals(now, result.fetchedAt)
        assertEquals(StationFreshness.Fresh, result.freshness)
    }

    @Test
    fun `observeNearbyStations cancels old freshness boundary and reschedules from new snapshot`() = runTest {
        val mutableClock = MutableClock(now)
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val snapshots = MutableSharedFlow<StationBucketSnapshot>(extraBufferCapacity = 2)
        val repository = repository(
            stationBucketSnapshotObserver = StationBucketSnapshotObserver { _, _, _, _ -> snapshots },
            clock = mutableClock,
        )
        val emissions = mutableListOf<StationSearchResult>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNearbyStations(query).take(3).toList(emissions)
        }

        snapshots.emit(bucketSnapshot(cacheKey, fetchedAt = now, stationId = "old"))
        runCurrent()
        assertEquals(listOf(StationFreshness.Fresh), emissions.map { it.freshness })

        mutableClock.advance(Duration.ofMinutes(4))
        advanceTimeBy(Duration.ofMinutes(4).toMillis())
        snapshots.emit(bucketSnapshot(cacheKey, fetchedAt = mutableClock.instant(), stationId = "new"))
        runCurrent()

        mutableClock.advance(Duration.ofMinutes(1).plusMillis(1))
        advanceTimeBy(Duration.ofMinutes(1).plusMillis(1).toMillis())
        runCurrent()
        assertEquals(listOf(StationFreshness.Fresh, StationFreshness.Fresh), emissions.map { it.freshness })

        mutableClock.advance(Duration.ofMinutes(4))
        advanceTimeBy(Duration.ofMinutes(4).toMillis())
        runCurrent()

        assertEquals(
            listOf(StationFreshness.Fresh, StationFreshness.Fresh, StationFreshness.Stale),
            emissions.map { it.freshness },
        )
        assertTrue(job.isCompleted)
    }

    @Test
    fun `cached empty snapshot ages without cache writes or database emission`() = runTest {
        val mutableClock = MutableClock(now)
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        val snapshots = MutableSharedFlow<StationBucketSnapshot>(extraBufferCapacity = 1)
        val repository = repository(
            stationCacheDao = stationCacheDao,
            stationBucketSnapshotObserver = StationBucketSnapshotObserver { _, _, _, _ -> snapshots },
            clock = mutableClock,
        )
        val emissions = mutableListOf<StationSearchResult>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNearbyStations(query).take(2).toList(emissions)
        }

        snapshots.emit(bucketSnapshot(cacheKey, fetchedAt = now, stationId = null))
        runCurrent()
        mutableClock.advance(Duration.ofMinutes(5).plusMillis(1))
        advanceTimeBy(Duration.ofMinutes(5).plusMillis(1).toMillis())
        runCurrent()

        assertEquals(listOf(StationFreshness.Fresh, StationFreshness.Stale), emissions.map { it.freshness })
        assertTrue(emissions.all { it.hasCachedSnapshot && it.stations.isEmpty() })
        assertEquals(0, stationCacheDao.replaceSnapshotCalls.size)
        assertTrue(stationCacheDao.pruneCutoffCalls.isEmpty())
        assertTrue(job.isCompleted)
    }

    @Test
    fun `watch metadata emission reuses current freshness boundary`() = runTest {
        val mutableClock = MutableClock(now)
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val snapshots = MutableSharedFlow<StationBucketSnapshot>(extraBufferCapacity = 1)
        val watchedStationDao = RecordingWatchedStationDao()
        val repository = repository(
            stationBucketSnapshotObserver = StationBucketSnapshotObserver { _, _, _, _ -> snapshots },
            watchedStationDao = watchedStationDao,
            clock = mutableClock,
        )
        val emissions = mutableListOf<StationSearchResult>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNearbyStations(query).take(3).toList(emissions)
        }

        snapshots.emit(bucketSnapshot(cacheKey, fetchedAt = now, stationId = "station-1"))
        runCurrent()
        mutableClock.advance(Duration.ofMinutes(4))
        advanceTimeBy(Duration.ofMinutes(4).toMillis())
        watchedStationDao.upsert(
            watched(
                stationId = "station-1",
                watchedAt = mutableClock.instant(),
            ),
        )
        runCurrent()

        mutableClock.advance(Duration.ofMinutes(1).plusMillis(1))
        advanceTimeBy(Duration.ofMinutes(1).plusMillis(1).toMillis())
        runCurrent()

        assertEquals(
            listOf(StationFreshness.Fresh, StationFreshness.Fresh, StationFreshness.Stale),
            emissions.map { it.freshness },
        )
        assertEquals(listOf(false, true, true), emissions.map { it.stations.single().isWatched })
        assertTrue(job.isCompleted)
    }

    @Test
    fun `freshness transition does not resubscribe cold metadata streams`() = runTest {
        val mutableClock = MutableClock(now)
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val watchedStationDao = ColdOneShotWatchedStationDao()
        val stationPriceHistoryDao = ColdOneShotStationPriceHistoryDao()
        val repository = repository(
            stationBucketSnapshotObserver = StationBucketSnapshotObserver { _, _, _, _ ->
                flowOf(bucketSnapshot(cacheKey, fetchedAt = now, stationId = "station-1"))
            },
            stationPriceHistoryDao = stationPriceHistoryDao,
            watchedStationDao = watchedStationDao,
            clock = mutableClock,
        )
        val emissions = mutableListOf<StationSearchResult>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNearbyStations(query).take(2).toList(emissions)
        }

        runCurrent()
        mutableClock.advance(Duration.ofMinutes(5).plusMillis(1))
        advanceTimeBy(Duration.ofMinutes(5).plusMillis(1).toMillis())
        runCurrent()

        assertEquals(listOf(StationFreshness.Fresh, StationFreshness.Stale), emissions.map { it.freshness })
        assertEquals(1, watchedStationDao.observeSubscriptions)
        assertEquals(1, stationPriceHistoryDao.observeSubscriptions)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `snapshot without marker remains no cache and does not start freshness timer`() = runTest {
        val mutableClock = MutableClock(now)
        val repository = repository(
            stationBucketSnapshotObserver = StationBucketSnapshotObserver { _, _, _, _ ->
                flowOf(StationBucketSnapshot(marker = null, rows = emptyList()))
            },
            clock = mutableClock,
        )

        val result = repository.observeNearbyStations(stationQuery()).first()

        assertEquals(StationFreshness.Stale, result.freshness)
        assertEquals(null, result.fetchedAt)
        assertEquals(false, result.hasCachedSnapshot)
        assertTrue(result.stations.isEmpty())
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `refreshNearbyStations records unexpected throwable via crashReporter and rethrows as StationRefreshException`() = runTest {
        val crashReporter = FakeCrashReporter()
        val query = stationQuery()
        val boom = IllegalStateException("unexpected boom")
        val repository = repository(
            remoteDataSource = ThrowingStationRemoteDataSource(boom),
            crashReporter = crashReporter,
        )

        assertThrows(StationRefreshException::class.java) {
            runBlocking { repository.refreshNearbyStations(query) }
        }

        assertEquals(1, crashReporter.records.size)
        assertEquals(boom, crashReporter.records.first().throwable)
    }

    @Test
    fun `refreshNearbyStations preserves unknown failure when crash reporter throws`() = runTest {
        val original = IllegalStateException("unexpected boom")
        val repository = repository(
            remoteDataSource = ThrowingStationRemoteDataSource(original),
            crashReporter = ThrowingCrashReporter(IllegalStateException("reporter failed")),
        )

        val error = assertThrows(StationRefreshException::class.java) {
            runBlocking { repository.refreshNearbyStations(stationQuery()) }
        }

        assertEquals(StationRefreshFailureReason.Unknown, error.reason)
        assertEquals(original, error.cause)
    }

    @Test
    fun `refreshNearbyStations does not record StationRefreshException via crashReporter`() = runTest {
        val crashReporter = FakeCrashReporter()
        val query = stationQuery()
        val repository = repository(
            remoteDataSource = FakeStationRemoteDataSource(
                RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network),
            ),
            crashReporter = crashReporter,
        )

        assertThrows(StationRefreshException::class.java) {
            runBlocking { repository.refreshNearbyStations(query) }
        }

        assertEquals(0, crashReporter.records.size)
    }

    @Test
    fun `newer response finishing first remains persisted when older response finishes later`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(CACHE_BUCKET_METERS)
        val olderFetch = PendingFetch()
        val newerFetch = PendingFetch()
        val remote = ControlledStationRemoteDataSource(olderFetch, newerFetch)
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val transactions = ImmediateDatabaseTransactionRunner()
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            transactionRunner = transactions,
        )

        val older = launch { repository.refreshNearbyStations(query) }
        olderFetch.started.await()
        val newer = launch { repository.refreshNearbyStations(query) }
        newerFetch.started.await()
        newerFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("newer"))))
        newer.join()
        olderFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("older"))))
        older.join()

        assertEquals(listOf("newer"), cacheDao.snapshotFor(cacheKey).map { it.stationId })
        assertEquals(now.toEpochMilli(), cacheDao.markerFor(cacheKey)?.fetchedAtEpochMillis)
        assertEquals(1, transactions.invocations)
        assertEquals(1, cacheDao.replaceSnapshotRecords.size)
        assertEquals(listOf("newer"), cacheDao.replaceSnapshotRecords.single().entities.map { it.stationId })
        assertEquals(now.toEpochMilli(), cacheDao.replaceSnapshotRecords.single().fetchedAtEpochMillis)
        assertEquals(
            listOf(listOf("newer" to now.toEpochMilli())),
            historyDao.insertAllCalls.map { call -> call.map { it.stationId to it.fetchedAtEpochMillis } },
        )
        assertEquals(listOf("newer" to query.fuelType.name), historyDao.keepLatestTenCalls)
        assertEquals(expectedPruneCutoffs(now), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
        assertEquals(listOf(query, query), remote.calls)
    }

    @Test
    fun `latest empty response clears rows and late older nonempty response writes nothing`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(CACHE_BUCKET_METERS)
        val olderFetch = PendingFetch()
        val newerFetch = PendingFetch()
        val cacheDao = RecordingStationCacheDao().apply {
            seed(stationEntity(cacheKey, stationId = "cached", fetchedAt = now.minusSeconds(60)))
        }
        val historyDao = RecordingStationPriceHistoryDao()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val transactions = ImmediateDatabaseTransactionRunner()
        val remote = ControlledStationRemoteDataSource(olderFetch, newerFetch)
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            transactionRunner = transactions,
        )

        val older = launch { repository.refreshNearbyStations(query) }
        olderFetch.started.await()
        val newer = launch { repository.refreshNearbyStations(query) }
        newerFetch.started.await()
        newerFetch.result.complete(RemoteStationFetchResult.Success(emptyList()))
        newer.join()
        olderFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("late-older"))))
        older.join()

        assertTrue(cacheDao.snapshotFor(cacheKey).isEmpty())
        assertEquals(now.toEpochMilli(), cacheDao.markerFor(cacheKey)?.fetchedAtEpochMillis)
        assertEquals(1, transactions.invocations)
        assertEquals(1, cacheDao.replaceSnapshotRecords.size)
        assertTrue(cacheDao.replaceSnapshotRecords.single().entities.isEmpty())
        assertEquals(now.toEpochMilli(), cacheDao.replaceSnapshotRecords.single().fetchedAtEpochMillis)
        assertEquals(listOf(emptyList<StationPriceHistoryEntity>()), historyDao.insertAllCalls)
        assertTrue(historyDao.keepLatestTenCalls.isEmpty())
        assertEquals(expectedPruneCutoffs(now), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(query, query), remote.calls)
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
    }

    @Test
    fun `superseded refresh emits no SearchRefreshed history or prune writes`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(CACHE_BUCKET_METERS)
        val olderFetch = PendingFetch()
        val newerFetch = PendingFetch()
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val transactions = ImmediateDatabaseTransactionRunner()
        val remote = ControlledStationRemoteDataSource(olderFetch, newerFetch)
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            transactionRunner = transactions,
        )

        val older = launch { repository.refreshNearbyStations(query) }
        olderFetch.started.await()
        val newer = launch { repository.refreshNearbyStations(query) }
        newerFetch.started.await()
        newerFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("latest"))))
        newer.join()
        olderFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("superseded"))))
        older.join()

        assertEquals(1, transactions.invocations)
        assertEquals(listOf("latest"), cacheDao.snapshotFor(cacheKey).map { it.stationId })
        assertEquals(now.toEpochMilli(), cacheDao.markerFor(cacheKey)?.fetchedAtEpochMillis)
        assertEquals(listOf("latest"), cacheDao.replaceSnapshotRecords.single().entities.map { it.stationId })
        assertEquals(
            listOf(listOf("latest" to now.toEpochMilli())),
            historyDao.insertAllCalls.map { call -> call.map { it.stationId to it.fetchedAtEpochMillis } },
        )
        assertEquals(listOf("latest" to query.fuelType.name), historyDao.keepLatestTenCalls)
        assertEquals(expectedPruneCutoffs(now), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
        assertEquals(listOf(query, query), remote.calls)
    }

    @Test
    fun `fetchedAt is captured at validated latest write time not request start`() = runTest(timeout = 10.seconds) {
        val mutableClock = MutableClock(now)
        val query = stationQuery()
        val cacheKey = query.toCacheKey(CACHE_BUCKET_METERS)
        val fetch = PendingFetch()
        val cacheDao = RecordingStationCacheDao()
        val transactions = ImmediateDatabaseTransactionRunner()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val historyDao = RecordingStationPriceHistoryDao()
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = ControlledStationRemoteDataSource(fetch),
            clock = mutableClock,
            analytics = analytics,
            transactionRunner = transactions,
        )

        val refresh = launch { repository.refreshNearbyStations(query) }
        fetch.started.await()
        mutableClock.advance(Duration.ofMinutes(2))
        fetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("station-1"))))
        refresh.join()

        val writeTime = now.plus(Duration.ofMinutes(2))
        assertEquals(writeTime.toEpochMilli(), cacheDao.markerFor(cacheKey)?.fetchedAtEpochMillis)
        assertEquals(writeTime.toEpochMilli(), cacheDao.snapshotFor(cacheKey).single().fetchedAtEpochMillis)
        assertEquals(writeTime.toEpochMilli(), cacheDao.replaceSnapshotRecords.single().fetchedAtEpochMillis)
        assertEquals(writeTime.toEpochMilli(), historyDao.insertAllCalls.single().single().fetchedAtEpochMillis)
        assertEquals(1, transactions.invocations)
        assertEquals(listOf("station-1" to query.fuelType.name), historyDao.keepLatestTenCalls)
        assertEquals(expectedPruneCutoffs(writeTime), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
    }

    @Test
    fun `cancelled older refresh cannot overwrite newer snapshot and releases its ticket`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(CACHE_BUCKET_METERS)
        val olderFetch = PendingFetch()
        val newerFetch = PendingFetch()
        val gate = LatestRefreshGate()
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val transactions = ImmediateDatabaseTransactionRunner()
        val remote = ControlledStationRemoteDataSource(olderFetch, newerFetch)
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            transactionRunner = transactions,
            latestRefreshGate = gate,
        )

        val older = launch { repository.refreshNearbyStations(query) }
        olderFetch.started.await()
        val newer = launch { repository.refreshNearbyStations(query) }
        newerFetch.started.await()
        newerFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("newer"))))
        newer.join()
        older.cancelAndJoin()

        assertEquals(listOf("newer"), cacheDao.snapshotFor(cacheKey).map { it.stationId })
        assertEquals(now.toEpochMilli(), cacheDao.markerFor(cacheKey)?.fetchedAtEpochMillis)
        assertEquals(1, transactions.invocations)
        assertEquals(listOf("newer"), cacheDao.replaceSnapshotRecords.single().entities.map { it.stationId })
        assertEquals(
            listOf(listOf("newer" to now.toEpochMilli())),
            historyDao.insertAllCalls.map { call -> call.map { it.stationId to it.fetchedAtEpochMillis } },
        )
        assertEquals(listOf("newer" to query.fuelType.name), historyDao.keepLatestTenCalls)
        assertEquals(expectedPruneCutoffs(now), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
        assertEquals(listOf(query, query), remote.calls)
        val afterCleanup = gate.begin(cacheKey)
        assertEquals(1L, afterCleanup.generation)
        gate.complete(afterCleanup)
    }

    @Test
    fun `different cache keys can finish and commit independently`() = runTest(timeout = 10.seconds) {
        val firstQuery = stationQuery()
        val secondQuery = firstQuery.copy(
            coordinates = Coordinates(
                latitude = firstQuery.coordinates.latitude + 0.01,
                longitude = firstQuery.coordinates.longitude,
            ),
        )
        val firstFetch = PendingFetch()
        val secondFetch = PendingFetch()
        val remote = ControlledStationRemoteDataSource(firstFetch, secondFetch)
        val transactions = FirstTransactionBlockingRunner()
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            transactionRunner = transactions,
        )

        val first = launch { repository.refreshNearbyStations(firstQuery) }
        firstFetch.started.await()
        val second = launch { repository.refreshNearbyStations(secondQuery) }
        secondFetch.started.await()
        firstFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("first"))))
        transactions.firstEntered.await()
        secondFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("second"))))
        runCurrent()

        assertTrue(second.isCompleted)
        assertFalse(first.isCompleted)
        transactions.releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(2, transactions.invocations)
        val firstKey = firstQuery.toCacheKey(CACHE_BUCKET_METERS)
        val secondKey = secondQuery.toCacheKey(CACHE_BUCKET_METERS)
        assertEquals(listOf("first"), cacheDao.snapshotFor(firstKey).map { it.stationId })
        assertEquals(listOf("second"), cacheDao.snapshotFor(secondKey).map { it.stationId })
        assertEquals(now.toEpochMilli(), cacheDao.markerFor(firstKey)?.fetchedAtEpochMillis)
        assertEquals(now.toEpochMilli(), cacheDao.markerFor(secondKey)?.fetchedAtEpochMillis)
        assertEquals(listOf("second", "first"), cacheDao.replaceSnapshotRecords.map { it.entities.single().stationId })
        assertEquals(listOf("second", "first"), historyDao.insertAllCalls.map { it.single().stationId })
        assertEquals(
            listOf("second" to secondQuery.fuelType.name, "first" to firstQuery.fuelType.name),
            historyDao.keepLatestTenCalls,
        )
        assertEquals(expectedPruneCutoffs(now, now), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(expectedSearchRefreshed(secondQuery), expectedSearchRefreshed(firstQuery)), analytics.events)
        assertEquals(listOf(firstQuery, secondQuery), remote.calls)
    }

    @Test
    fun `superseded remote failure returns silently without retry or crash reporting`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val olderFetch = PendingFetch()
        val newerFetch = PendingFetch()
        val remote = ControlledStationRemoteDataSource(olderFetch, newerFetch)
        val crashReporter = FakeCrashReporter()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val transactions = ImmediateDatabaseTransactionRunner()
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            crashReporter = crashReporter,
            transactionRunner = transactions,
        )

        val older = async { repository.refreshNearbyStations(query) }
        olderFetch.started.await()
        val newer = async { repository.refreshNearbyStations(query) }
        newerFetch.started.await()
        newerFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("newer"))))
        newer.await()
        olderFetch.result.complete(RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network))
        older.await()

        assertTrue(crashReporter.records.isEmpty())
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
        assertEquals(1, transactions.invocations)
        assertEquals(listOf("newer"), cacheDao.replaceSnapshotRecords.single().entities.map { it.stationId })
        assertEquals(listOf("newer"), historyDao.insertAllCalls.single().map { it.stationId })
        assertEquals(listOf("newer" to query.fuelType.name), historyDao.keepLatestTenCalls)
        assertEquals(expectedPruneCutoffs(now), cacheDao.pruneCutoffCalls)
        assertEquals(listOf(query, query), remote.calls)
    }

    @Test
    fun `supersession during retry delay skips second request and retry event`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val olderFirstFetch = PendingFetch()
        val newerFetch = PendingFetch()
        val remote = ControlledStationRemoteDataSource(olderFirstFetch, newerFetch)
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val transactions = ImmediateDatabaseTransactionRunner()
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = remote,
            analytics = analytics,
            transactionRunner = transactions,
        )

        val older = async { repository.refreshNearbyStations(query) }
        olderFirstFetch.started.await()
        olderFirstFetch.result.complete(RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network))
        runCurrent()
        val newer = async { repository.refreshNearbyStations(query) }
        newerFetch.started.await()
        newerFetch.result.complete(RemoteStationFetchResult.Success(listOf(remoteStation("newer"))))
        newer.await()
        advanceUntilIdle()
        older.await()

        assertEquals(listOf(query, query), remote.calls)
        assertEquals(listOf(expectedSearchRefreshed(query)), analytics.events)
        assertEquals(1, transactions.invocations)
        assertEquals(listOf("newer"), cacheDao.replaceSnapshotRecords.single().entities.map { it.stationId })
        assertEquals(listOf("newer"), historyDao.insertAllCalls.single().map { it.stationId })
        assertEquals(listOf("newer" to query.fuelType.name), historyDao.keepLatestTenCalls)
        assertEquals(expectedPruneCutoffs(now), cacheDao.pruneCutoffCalls)
    }

    @Test
    fun `caller cancellation propagates and is not treated as supersession`() = runTest(timeout = 10.seconds) {
        val fetch = PendingFetch()
        val repository = repository(
            remoteDataSource = ControlledStationRemoteDataSource(fetch),
        )
        val refresh = async { repository.refreshNearbyStations(stationQuery()) }
        fetch.started.await()

        refresh.cancel()
        val cancellation = assertFailsWith<CancellationException> { refresh.await() }

        assertEquals(true, refresh.isCancelled)
        assertFalse(cancellation.message == "Refresh was superseded")
    }

    @Test
    fun `cancellation while complete is suspended still releases gate entry`() = runTest(timeout = 10.seconds) {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(CACHE_BUCKET_METERS)
        val fetch = PendingFetch()
        val gate = LatestRefreshGate()
        val cacheDao = RecordingStationCacheDao()
        val historyDao = RecordingStationPriceHistoryDao()
        val analytics = RepositoryDoubles.RecordingStationEventLogger()
        val transactions = ImmediateDatabaseTransactionRunner()
        val repository = repository(
            stationCacheDao = cacheDao,
            stationPriceHistoryDao = historyDao,
            remoteDataSource = ControlledStationRemoteDataSource(fetch),
            analytics = analytics,
            transactionRunner = transactions,
            latestRefreshGate = gate,
        )
        val releaseGate = CompletableDeferred<Unit>()
        val gateEntered = CompletableDeferred<Unit>()

        val refresh = async { repository.refreshNearbyStations(query) }
        fetch.started.await()
        val gateBlocker = launch {
            gate.commitIfLatest(RefreshTicket(cacheKey, generation = 1L)) {
                gateEntered.complete(Unit)
                releaseGate.await()
            }
        }
        gateEntered.await()
        refresh.cancel()
        runCurrent()

        assertFalse(refresh.isCompleted)
        releaseGate.complete(Unit)
        gateBlocker.join()
        assertFailsWith<CancellationException> { refresh.await() }
        assertTrue(cacheDao.snapshotFor(cacheKey).isEmpty())
        assertEquals(null, cacheDao.markerFor(cacheKey))
        assertTrue(cacheDao.replaceSnapshotRecords.isEmpty())
        assertTrue(historyDao.insertAllCalls.isEmpty())
        assertTrue(historyDao.keepLatestTenCalls.isEmpty())
        assertTrue(cacheDao.pruneCutoffCalls.isEmpty())
        assertTrue(analytics.events.isEmpty())
        assertEquals(0, transactions.invocations)

        val afterCleanup = gate.begin(cacheKey)
        assertEquals(1L, afterCleanup.generation)
        gate.complete(afterCleanup)
    }

    private fun repository(
        stationCacheDao: StationCacheDao = RecordingStationCacheDao(),
        stationBucketSnapshotObserver: StationBucketSnapshotObserver = RecordingStationBucketSnapshotObserver(stationCacheDao),
        stationPriceHistoryDao: StationPriceHistoryDao = RecordingStationPriceHistoryDao(),
        watchedStationDao: WatchedStationDao = RecordingWatchedStationDao(),
        remoteDataSource: StationRemoteDataSource = FakeStationRemoteDataSource(
            RemoteStationFetchResult.Success(emptyList()),
        ),
        analytics: StationEventLogger = RepositoryDoubles.RecordingStationEventLogger(),
        crashReporter: CrashReporter = FakeCrashReporter(),
        transactionRunner: DatabaseTransactionRunner = ImmediateDatabaseTransactionRunner(),
        clock: Clock = this.clock,
        latestRefreshGate: LatestRefreshGate = LatestRefreshGate(),
    ) = DefaultStationRepository(
        stationCacheDao = stationCacheDao,
        stationBucketSnapshotObserver = stationBucketSnapshotObserver,
        stationPriceHistoryDao = stationPriceHistoryDao,
        watchedStationDao = watchedStationDao,
        remoteDataSource = remoteDataSource,
        cachePolicy = StationCachePolicy(),
        retryPolicy = StationRetryPolicy(analytics),
        stationEventLogger = analytics,
        crashReporter = crashReporter,
        transactionRunner = transactionRunner,
        clock = clock,
        freshnessTicker = StationFreshnessTicker(StationCachePolicy(), clock),
        latestRefreshGate = latestRefreshGate,
    )

    private fun remoteStation(stationId: String) = RemoteStation(
        stationId = stationId,
        name = "Station $stationId",
        brandCode = "GSC",
        priceWon = 1_699,
        coordinates = Coordinates(37.498095, 127.027610),
    )

    private fun expectedSearchRefreshed(query: StationQuery) = StationEvent.SearchRefreshed(
        radius = query.radius,
        fuelType = query.fuelType,
        sortOrder = query.sortOrder,
        stale = false,
    )

    private fun expectedPruneCutoffs(vararg fetchedAt: Instant) = fetchedAt
        .map { StationCachePolicy().pruneCutoff(it).toEpochMilli() }

    private fun bucketSnapshot(
        cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey,
        fetchedAt: Instant,
        stationId: String?,
    ) = StationBucketSnapshot(
        marker = StationCacheSnapshotEntity(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
            fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
        ),
        rows = stationId?.let { listOf(stationEntity(cacheKey, stationId = it, fetchedAt = fetchedAt)) }.orEmpty(),
    )

    private fun stationQuery(
        brandFilter: BrandFilter = BrandFilter.ALL,
        fuelType: FuelType = FuelType.GASOLINE,
        sortOrder: SortOrder = SortOrder.DISTANCE,
    ) = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = fuelType,
        brandFilter = brandFilter,
        sortOrder = sortOrder,
    )

    private fun stationEntity(
        cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey,
        stationId: String,
        brandCode: String = "GSC",
        priceWon: Int = 1_699,
        latitude: Double = 37.498095,
        longitude: Double = 127.027610,
        fetchedAt: Instant = now,
    ) = StationCacheEntity(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType.name,
        stationId = stationId,
        brandCode = brandCode,
        name = "Station $stationId",
        priceWon = priceWon,
        latitude = latitude,
        longitude = longitude,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )

    private companion object {
        const val CACHE_BUCKET_METERS = 250
    }

    private class ThrowingCrashReporter(private val throwable: Throwable) : CrashReporter {
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>): Nothing = throw this.throwable

        override fun log(message: String) = Unit
    }

    private class ColdOneShotWatchedStationDao : WatchedStationDao {
        var observeSubscriptions: Int = 0
            private set

        override suspend fun upsert(entity: WatchedStationEntity) = error("Not used")

        override suspend fun delete(stationId: String) = error("Not used")

        override fun observeWatchedStationIds(): Flow<List<String>> = flow {
            observeSubscriptions++
            if (observeSubscriptions == 1) emit(emptyList())
            awaitCancellation()
        }

        override fun observeWatchedStations(): Flow<List<WatchedStationEntity>> = error("Not used")
    }

    private class ColdOneShotStationPriceHistoryDao : StationPriceHistoryDao {
        var observeSubscriptions: Int = 0
            private set

        override suspend fun insert(entity: StationPriceHistoryEntity) = error("Not used")

        override suspend fun insertAll(entities: List<StationPriceHistoryEntity>) = error("Not used")

        override fun observeByStationIds(stationIds: List<String>): Flow<List<StationPriceHistoryEntity>> = error("Not used")

        override fun observeByStationIdsAndFuelType(stationIds: List<String>, fuelType: String): Flow<List<StationPriceHistoryEntity>> =
            flow {
                observeSubscriptions++
                if (observeSubscriptions == 1) emit(emptyList())
                awaitCancellation()
            }

        override suspend fun keepLatestTenByStationAndFuelType(stationId: String, fuelType: String) = error("Not used")
    }

    private class PendingFetch {
        val started = CompletableDeferred<StationQuery>()
        val result = CompletableDeferred<RemoteStationFetchResult>()
    }

    private class ControlledStationRemoteDataSource(vararg pendingFetches: PendingFetch) : StationRemoteDataSource {
        private val pendingFetches = pendingFetches.toList()
        val calls = mutableListOf<StationQuery>()

        override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
            val index = calls.size
            calls += query
            val pending = pendingFetches[index]
            pending.started.complete(query)
            return pending.result.await()
        }
    }

    private class FirstTransactionBlockingRunner : DatabaseTransactionRunner {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var invocations = 0
            private set

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            invocations += 1
            if (invocations == 1) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
            return block()
        }
    }

    private class MutableClock(private var current: Instant, private val zoneId: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
