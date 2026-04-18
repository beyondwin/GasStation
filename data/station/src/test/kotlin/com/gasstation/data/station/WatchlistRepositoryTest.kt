package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationCacheSnapshotEntity
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationPriceDelta
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class WatchlistRepositoryTest {
    private val now = Instant.parse("2026-04-18T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

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

        val items = repository.observeWatchlist(origin).first()

        assertEquals(listOf("station-1", "station-2"), items.map { it.station.id })
        assertEquals(StationPriceDelta.Decreased(30), items[0].priceDelta)
        assertEquals(StationPriceDelta.Unavailable, items[1].priceDelta)
        assertEquals(now.minusSeconds(30), items[0].lastSeenAt)
        assertEquals(now.minusSeconds(90), items[1].lastSeenAt)
        assertEquals("Fallback One", items[0].station.name)
        assertEquals(1_590, items[1].station.price.value)
        assertTrue(items.all { it.station.distance.value >= 0 })
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

        val item = repository.observeWatchlist(origin).first().single()

        assertEquals("station-3", item.station.id)
        assertEquals("Cached Snapshot", item.station.name)
        assertEquals(1_620, item.station.price.value)
        assertEquals(StationPriceDelta.Unavailable, item.priceDelta)
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

        val item = repository.observeWatchlist(origin).first().single()

        assertEquals(1_540, item.station.price.value)
        assertEquals(StationPriceDelta.Decreased(20), item.priceDelta)
        assertEquals(now.minusSeconds(90), item.lastSeenAt)
    }

    @Test
    fun `observeWatchlist drops watched entries with no last known snapshot or history`() = runBlocking {
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

        assertTrue(repository.observeWatchlist(origin).first().isEmpty())
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
        stationPriceHistoryDao = stationPriceHistoryDao,
        watchedStationDao = watchedStationDao,
        remoteDataSource = NoOpStationRemoteDataSource,
        seedRemoteDataSource = Optional.empty(),
        cachePolicy = StationCachePolicy(),
        clock = clock,
    )

    private object NoOpStationRemoteDataSource : StationRemoteDataSource {
        override suspend fun fetchStations(query: com.gasstation.domain.station.model.StationQuery): RemoteStationFetchResult {
            error("refreshNearbyStations is not used in watchlist repository tests")
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

        override fun observeLatestStationsByIds(
            stationIds: List<String>,
        ): Flow<List<StationCacheEntity>> = flowOf(emptyList())

        override suspend fun upsertAll(entities: List<StationCacheEntity>) = Unit

        override suspend fun upsertSnapshot(snapshot: StationCacheSnapshotEntity) = Unit

        override suspend fun deleteStations(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
        ) = Unit

        override suspend fun pruneStationsOlderThan(cutoffEpochMillis: Long) = Unit

        override suspend fun pruneSnapshotsOlderThan(cutoffEpochMillis: Long) = Unit
    }

    private class RecordingWatchlistStationCacheDao(
        private val cachedStations: List<StationCacheEntity>,
    ) : EmptyStationCacheDao() {
        override fun observeLatestStationsByIds(
            stationIds: List<String>,
        ): Flow<List<StationCacheEntity>> = flowOf(
            cachedStations.filter { it.stationId in stationIds },
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
    ) = StationCacheEntity(
        latitudeBucket = 0,
        longitudeBucket = 0,
        radiusMeters = 3_000,
        fuelType = "GASOLINE",
        stationId = stationId,
        brandCode = brandCode,
        name = name,
        priceWon = priceWon,
        latitude = latitude,
        longitude = longitude,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}
