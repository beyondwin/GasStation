package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationCacheSnapshotEntity
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.observability.CrashReporter
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchlistQuery
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WatchlistRepositoryTest {
    private val now = Instant.parse("2026-04-18T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `watchlist uses requested fuel and never substitutes newer other fuel`() = runBlocking {
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-1",
                        name = "Gasoline snapshot",
                        brandCode = "GSC",
                        priceWon = 1_700,
                        latitude = 37.498095,
                        longitude = 127.027610,
                        fetchedAt = now.minusSeconds(60),
                        fuelType = "GASOLINE",
                    ),
                    cachedStation(
                        stationId = "station-1",
                        name = "Diesel snapshot",
                        brandCode = "GSC",
                        priceWon = 1_520,
                        latitude = 37.498095,
                        longitude = 127.027610,
                        fetchedAt = now.minusSeconds(10),
                        fuelType = "DIESEL",
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-1",
                        watchedAt = now,
                        name = "Saved",
                        brandCode = "GSC",
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(
            WatchlistQuery(
                origin = Coordinates(37.498095, 127.027610),
                fuelType = FuelType.GASOLINE,
            ),
        ).first().single()

        assertEquals(1_700, item.price?.value)
    }

    @Test
    fun `watchlist retains saved identity when selected fuel has no price`() = runBlocking {
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-1",
                        name = "Other Fuel Identity",
                        brandCode = "HDO",
                        priceWon = 1_700,
                        latitude = 37.400000,
                        longitude = 127.100000,
                        fetchedAt = now.minusSeconds(10),
                        fuelType = "GASOLINE",
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-1",
                        name = "Saved Without Diesel",
                        brandCode = "RTX",
                        latitude = 37.498095,
                        longitude = 127.027610,
                        watchedAt = now,
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(
            WatchlistQuery(
                origin = Coordinates(37.500000, 127.030000),
                fuelType = FuelType.DIESEL,
            ),
        ).first().single()

        assertEquals("station-1", item.id)
        assertEquals("Saved Without Diesel", item.name)
        assertEquals(Brand.RTX, item.brand)
        assertEquals(Coordinates(37.498095, 127.027610), item.coordinates)
        assertEquals(null, item.price)
        assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
        assertEquals(null, item.lastSeenAt)
    }

    @Test
    fun `watchlist preserves watched time order independent of nearby filters and sort`() = runBlocking {
        val repository = repository(
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(stationId = "older", watchedAt = now.minusSeconds(30)),
                    watched(stationId = "newer", watchedAt = now.minusSeconds(5)),
                ),
            ),
        )

        val items = repository.observeWatchlist(
            WatchlistQuery(
                origin = Coordinates(37.498095, 127.027610),
                fuelType = FuelType.PREMIUM_GASOLINE,
            ),
        ).first()

        assertEquals(listOf("newer", "older"), items.map { it.id })
    }

    @Test
    fun `removeWatchedStation deletes saved row by stable id`() = runBlocking {
        val watchedStationDao = RecordingWatchedStationDao(
            watchedStations = listOf(watched(stationId = "station-1", watchedAt = now)),
        )
        val repository = repository(watchedStationDao = watchedStationDao)

        repository.removeWatchedStation("station-1")

        assertTrue(watchedStationDao.currentWatchedStations().isEmpty())
        assertEquals(listOf("station-1"), watchedStationDao.deletedStationIds)
    }

    @Test
    fun `observeWatchlist returns watched stations sorted by watched time with latest known pricing`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-1",
                        priceWon = 1_680,
                        fetchedAt = now.minusSeconds(30),
                    ),
                    history(
                        stationId = "station-1",
                        priceWon = 1_710,
                        fetchedAt = now.minusSeconds(330),
                    ),
                    history(
                        stationId = "station-2",
                        priceWon = 1_590,
                        fetchedAt = now.minusSeconds(90),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-2",
                        name = "Fallback Two",
                        brandCode = "SKE",
                        latitude = 37.499095,
                        longitude = 127.028610,
                        watchedAt = now.minusSeconds(10),
                    ),
                    watched(
                        stationId = "station-1",
                        name = "Fallback One",
                        brandCode = "GSC",
                        latitude = 37.497095,
                        longitude = 127.026610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val items = repository.observeWatchlist(watchlistQuery(origin)).first()

        assertEquals(listOf("station-1", "station-2"), items.map { it.id })
        assertEquals(StationPriceDelta.Decreased(30), items[0].priceDelta)
        assertEquals(StationPriceDelta.Unavailable, items[1].priceDelta)
        assertEquals(now.minusSeconds(30), items[0].lastSeenAt)
        assertEquals(now.minusSeconds(90), items[1].lastSeenAt)
        assertEquals("Fallback One", items[0].name)
        assertEquals(1_590, items[1].price?.value)
        assertTrue(items.all { it.distance.value >= 0 })
    }

    @Test
    fun `observeWatchlist falls back to latest cached snapshot when history is missing`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-3",
                        name = "Cached Snapshot",
                        brandCode = "HDO",
                        priceWon = 1_620,
                        latitude = 37.500095,
                        longitude = 127.025610,
                        fetchedAt = now.minusSeconds(45),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-3",
                        name = "Watched Fallback",
                        brandCode = "HDO",
                        latitude = 37.490095,
                        longitude = 127.015610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(watchlistQuery(origin)).first().single()

        assertEquals("station-3", item.id)
        assertEquals("Cached Snapshot", item.name)
        assertEquals(1_620, item.price?.value)
        assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
        assertEquals(now.minusSeconds(45), item.lastSeenAt)
    }

    @Test
    fun `observeWatchlist builds summary from dao selected latest cache row`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-3",
                        name = "DAO Selected Snapshot",
                        brandCode = "RTX",
                        priceWon = 1_590,
                        latitude = 37.500095,
                        longitude = 127.025610,
                        fetchedAt = now.minusSeconds(45),
                        fuelType = "DIESEL",
                    ),
                ),
            ),
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-3",
                        fuelType = "DIESEL",
                        priceWon = 1_630,
                        fetchedAt = now.minusSeconds(120),
                    ),
                    history(
                        stationId = "station-3",
                        fuelType = "GASOLINE",
                        priceWon = 1_700,
                        fetchedAt = now.minusSeconds(30),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-3",
                        name = "Watched Fallback",
                        brandCode = "GSC",
                        latitude = 37.490095,
                        longitude = 127.015610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(
            watchlistQuery(origin, FuelType.DIESEL),
        ).first().single()

        assertEquals("DAO Selected Snapshot", item.name)
        assertEquals(Brand.RTX, item.brand)
        assertEquals(1_590, item.price?.value)
        assertEquals(StationPriceDelta.Decreased(40), item.priceDelta)
        assertEquals(now.minusSeconds(45), item.lastSeenAt)
    }

    @Test
    fun `observeWatchlist history fallback stays within one fuel type context`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-4",
                        fuelType = "GASOLINE",
                        priceWon = 1_690,
                        fetchedAt = now.minusSeconds(600),
                    ),
                    history(
                        stationId = "station-4",
                        fuelType = "DIESEL",
                        priceWon = 1_540,
                        fetchedAt = now.minusSeconds(90),
                    ),
                    history(
                        stationId = "station-4",
                        fuelType = "DIESEL",
                        priceWon = 1_560,
                        fetchedAt = now.minusSeconds(390),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-4",
                        name = "History Only",
                        brandCode = "RTX",
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(
            watchlistQuery(origin, FuelType.DIESEL),
        ).first().single()

        assertEquals(1_540, item.price?.value)
        assertEquals(StationPriceDelta.Decreased(20), item.priceDelta)
        assertEquals(now.minusSeconds(90), item.lastSeenAt)
    }

    @Test
    fun `observeWatchlist ignores invalid cached row when computing history fallback delta`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-invalid",
                        name = "Invalid Cached Snapshot",
                        brandCode = "GSC",
                        priceWon = -1,
                        latitude = 37.500095,
                        longitude = 127.025610,
                        fetchedAt = now.minusSeconds(10),
                    ),
                ),
            ),
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-invalid",
                        priceWon = 1_680,
                        fetchedAt = now.minusSeconds(30),
                    ),
                    history(
                        stationId = "station-invalid",
                        priceWon = 1_660,
                        fetchedAt = now.minusSeconds(330),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-invalid",
                        name = "Watched Fallback",
                        brandCode = "GSC",
                        latitude = 37.497095,
                        longitude = 127.026610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(watchlistQuery(origin)).first().single()

        assertEquals("Watched Fallback", item.name)
        assertEquals(1_680, item.price?.value)
        assertEquals(StationPriceDelta.Increased(20), item.priceDelta)
        assertEquals(now.minusSeconds(30), item.lastSeenAt)
    }

    @Test
    fun `watchlist keeps valid cache row when preceding history price is invalid`() = runBlocking {
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-cache-current",
                        name = "Valid Current Cache",
                        brandCode = "GSC",
                        priceWon = 1_700,
                        latitude = 37.498095,
                        longitude = 127.027610,
                        fetchedAt = now.minusSeconds(10),
                    ),
                ),
            ),
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-cache-current",
                        priceWon = -1,
                        fetchedAt = now.minusSeconds(30),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-cache-current",
                        watchedAt = now,
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(
            watchlistQuery(Coordinates(37.498095, 127.027610)),
        ).first().single()

        assertEquals("station-cache-current", item.id)
        assertEquals(1_700, item.price?.value)
        assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
    }

    @Test
    fun `watchlist keeps valid history row when older history price is invalid`() = runBlocking {
        val repository = repository(
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-history-current",
                        priceWon = 1_680,
                        fetchedAt = now.minusSeconds(30),
                    ),
                    history(
                        stationId = "station-history-current",
                        priceWon = -1,
                        fetchedAt = now.minusSeconds(330),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-history-current",
                        watchedAt = now,
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(
            watchlistQuery(Coordinates(37.498095, 127.027610)),
        ).first().single()

        assertEquals("station-history-current", item.id)
        assertEquals(1_680, item.price?.value)
        assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
    }

    @Test
    fun `observeWatchlist retains watched entries with no last known snapshot or history`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-4",
                        name = "Unknown Station",
                        brandCode = "GSC",
                        latitude = 37.490095,
                        longitude = 127.015610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(watchlistQuery(origin)).first().single()

        assertEquals("station-4", item.id)
        assertEquals("Unknown Station", item.name)
        assertEquals(null, item.price)
        assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
    }

    @Test
    fun `observeWatchlist drops watched entries whose fallback coordinates are out of range`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-bad",
                        priceWon = 1_680,
                        fetchedAt = now.minusSeconds(30),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-bad",
                        name = "Out Of Range",
                        brandCode = "GSC",
                        latitude = 200.0,
                        longitude = 127.026610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        assertTrue(repository.observeWatchlist(watchlistQuery(origin)).first().isEmpty())
    }

    @Test
    fun `updateWatchState upserts and deletes watched rows`() = runBlocking {
        val watchedStationDao = RecordingWatchedStationDao()
        val repository = repository(watchedStationDao = watchedStationDao)
        val station = Station(
            id = "station-1",
            name = "Watched Station",
            brand = Brand.GSC,
            price = MoneyWon(1_680),
            distance = DistanceMeters(120),
            coordinates = Coordinates(37.498095, 127.027610),
        )

        repository.updateWatchState(station = station, watched = true)

        val watchedRow = watchedStationDao.currentWatchedStations().single()

        assertEquals("station-1", watchedRow.stationId)
        assertEquals("Watched Station", watchedRow.name)
        assertEquals("GSC", watchedRow.brandCode)
        assertEquals(now.toEpochMilli(), watchedRow.watchedAtEpochMillis)

        repository.updateWatchState(station = station, watched = false)

        assertTrue(watchedStationDao.currentWatchedStations().isEmpty())
        assertEquals(listOf("station-1"), watchedStationDao.deletedStationIds)
    }

    private fun repository(
        stationCacheDao: StationCacheDao = EmptyStationCacheDao(),
        stationPriceHistoryDao: RecordingStationPriceHistoryDao = RecordingStationPriceHistoryDao(),
        watchedStationDao: RecordingWatchedStationDao = RecordingWatchedStationDao(),
    ) = DefaultStationRepository(
        stationCacheDao = stationCacheDao,
        stationBucketSnapshotObserver = RecordingStationBucketSnapshotObserver(stationCacheDao),
        stationPriceHistoryDao = stationPriceHistoryDao,
        watchedStationDao = watchedStationDao,
        remoteDataSource = NoOpStationRemoteDataSource,
        cachePolicy = StationCachePolicy(),
        retryPolicy = StationRetryPolicy(RecordingStationEventLogger()),
        stationEventLogger = RecordingStationEventLogger(),
        crashReporter = NoOpCrashReporter,
        transactionRunner = ImmediateDatabaseTransactionRunner(),
        clock = clock,
        freshnessTicker = StationFreshnessTicker(StationCachePolicy(), clock),
    )

    private fun watchlistQuery(origin: Coordinates, fuelType: FuelType = FuelType.GASOLINE) =
        WatchlistQuery(origin = origin, fuelType = fuelType)

    private object NoOpStationRemoteDataSource : StationRemoteDataSource {
        override suspend fun fetchStations(query: com.gasstation.domain.station.model.StationQuery): RemoteStationFetchResult {
            error("refreshNearbyStations is not used in watchlist repository tests")
        }
    }

    private object NoOpCrashReporter : CrashReporter {
        override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) = Unit
        override fun log(message: String) = Unit
    }

    private class RecordingStationEventLogger : StationEventLogger {
        val events = mutableListOf<StationEvent>()

        override fun log(event: StationEvent) {
            events += event
        }
    }

    private open class EmptyStationCacheDao : StationCacheDao() {
        override fun observeStations(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
        ): Flow<List<StationCacheEntity>> = flowOf(emptyList())

        override fun observeSnapshot(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
        ): Flow<StationCacheSnapshotEntity?> = flowOf(null)

        override suspend fun readStations(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
        ): List<StationCacheEntity> = emptyList()

        override suspend fun readSnapshot(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
        ): StationCacheSnapshotEntity? = null

        override fun observeLatestStationsByIds(stationIds: List<String>): Flow<List<StationCacheEntity>> = flowOf(emptyList())

        override fun observeLatestStationsByIdsAndFuelType(stationIds: List<String>, fuelType: String): Flow<List<StationCacheEntity>> =
            flowOf(emptyList())

        override suspend fun upsertAll(entities: List<StationCacheEntity>) = Unit

        override suspend fun upsertSnapshot(snapshot: StationCacheSnapshotEntity) = Unit

        override suspend fun deleteStations(latitudeBucket: Int, longitudeBucket: Int, radiusMeters: Int, fuelType: String) = Unit

        override suspend fun pruneStationsOlderThan(cutoffEpochMillis: Long) = Unit

        override suspend fun pruneSnapshotsOlderThan(cutoffEpochMillis: Long) = Unit
    }

    private class RecordingWatchlistStationCacheDao(private val cachedStations: List<StationCacheEntity>) : EmptyStationCacheDao() {
        override fun observeLatestStationsByIds(stationIds: List<String>): Flow<List<StationCacheEntity>> = flowOf(
            cachedStations.filter { it.stationId in stationIds },
        )

        override fun observeLatestStationsByIdsAndFuelType(stationIds: List<String>, fuelType: String): Flow<List<StationCacheEntity>> =
            flowOf(
                cachedStations
                    .filter { it.stationId in stationIds && it.fuelType == fuelType }
                    .groupBy { it.stationId }
                    .values
                    .map { rows ->
                        rows.sortedWith(
                            compareByDescending<StationCacheEntity> { it.fetchedAtEpochMillis }
                                .thenBy { it.radiusMeters }
                                .thenBy { it.latitudeBucket }
                                .thenBy { it.longitudeBucket },
                        ).first()
                    },
            )
    }

    private fun cachedStation(
        stationId: String,
        name: String,
        brandCode: String,
        priceWon: Int,
        latitude: Double,
        longitude: Double,
        fetchedAt: Instant,
        fuelType: String = "GASOLINE",
        radiusMeters: Int = 3_000,
        latitudeBucket: Int = 0,
        longitudeBucket: Int = 0,
    ) = StationCacheEntity(
        latitudeBucket = latitudeBucket,
        longitudeBucket = longitudeBucket,
        radiusMeters = radiusMeters,
        fuelType = fuelType,
        stationId = stationId,
        brandCode = brandCode,
        name = name,
        priceWon = priceWon,
        latitude = latitude,
        longitude = longitude,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}
