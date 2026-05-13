package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

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
    fun `refreshNearbyStations prefers seed data source when available`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "stale-station",
                fetchedAt = now.minusSeconds(600),
            ),
        )
        val repository = repository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(
                RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network),
            ),
            seedRemoteDataSource = Optional.of(
                FakeSeedStationRemoteDataSource(
                    RemoteStationFetchResult.Success(
                        listOf(
                            RemoteStation(
                                stationId = "seed-station",
                                name = "Seed Station",
                                brandCode = "SKE",
                                priceWon = 1_777,
                                coordinates = Coordinates(37.497927, 127.027583),
                            ),
                        ),
                    ),
                ),
            ),
        )

        repository.refreshNearbyStations(query)

        val refreshedStations = stationCacheDao.snapshotFor(cacheKey)
        assertEquals(listOf("seed-station"), refreshedStations.map { it.stationId })
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

    private fun repository(
        stationCacheDao: StationCacheDao = RecordingStationCacheDao(),
        stationPriceHistoryDao: RecordingStationPriceHistoryDao = RecordingStationPriceHistoryDao(),
        watchedStationDao: RecordingWatchedStationDao = RecordingWatchedStationDao(),
        remoteDataSource: StationRemoteDataSource = FakeStationRemoteDataSource(
            RemoteStationFetchResult.Success(emptyList()),
        ),
        seedRemoteDataSource: Optional<SeedStationRemoteDataSource> = Optional.empty(),
        analytics: StationEventLogger = RepositoryDoubles.RecordingStationEventLogger(),
        crashReporter: CrashReporter = FakeCrashReporter(),
    ) = DefaultStationRepository(
        stationCacheDao = stationCacheDao,
        stationPriceHistoryDao = stationPriceHistoryDao,
        watchedStationDao = watchedStationDao,
        remoteDataSource = remoteDataSource,
        seedRemoteDataSource = seedRemoteDataSource,
        cachePolicy = StationCachePolicy(),
        retryPolicy = StationRetryPolicy(analytics),
        stationEventLogger = analytics,
        crashReporter = crashReporter,
        clock = clock,
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
}
