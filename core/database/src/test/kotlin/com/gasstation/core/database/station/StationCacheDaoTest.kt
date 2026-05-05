package com.gasstation.core.database.station

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.GasStationDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationCacheDaoTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: StationCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.stationCacheDao()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `observeStations returns one bucket snapshot and replaceSnapshot swaps it atomically`() = runBlocking {
        val cacheKey = CacheKey(
            latitudeBucket = 16649,
            longitudeBucket = 50811,
            radiusMeters = 3_000,
            fuelType = "GASOLINE",
        )
        val otherKey = cacheKey.copy(latitudeBucket = cacheKey.latitudeBucket + 1)
        val initialStation = station(cacheKey, stationId = "station-1")
        val unrelatedStation = station(otherKey, stationId = "station-2")
        val replacementStation = station(cacheKey, stationId = "station-3")

        dao.replaceSnapshot(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType,
            fetchedAtEpochMillis = initialStation.fetchedAtEpochMillis,
            entities = listOf(initialStation),
        )
        dao.replaceSnapshot(
            latitudeBucket = otherKey.latitudeBucket,
            longitudeBucket = otherKey.longitudeBucket,
            radiusMeters = otherKey.radiusMeters,
            fuelType = otherKey.fuelType,
            fetchedAtEpochMillis = unrelatedStation.fetchedAtEpochMillis,
            entities = listOf(unrelatedStation),
        )

        val emissions = mutableListOf<List<StationCacheEntity>>()
        val firstEmissionReceived = CompletableDeferred<Unit>()
        val collectJob = launch {
            withTimeout(2_000) {
                dao.observeStations(
                    latitudeBucket = cacheKey.latitudeBucket,
                    longitudeBucket = cacheKey.longitudeBucket,
                    radiusMeters = cacheKey.radiusMeters,
                    fuelType = cacheKey.fuelType,
                )
                    .take(2)
                    .collectIndexed { index, stations ->
                        emissions += listOf(stations)
                        if (index == 0) {
                            firstEmissionReceived.complete(Unit)
                        }
                    }
            }
        }
        firstEmissionReceived.await()

        dao.replaceSnapshot(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType,
            fetchedAtEpochMillis = replacementStation.fetchedAtEpochMillis,
            entities = listOf(replacementStation),
        )

        collectJob.join()

        assertEquals(
            listOf(
                listOf(initialStation),
                listOf(replacementStation),
            ),
            emissions,
        )
    }

    @Test
    fun `observeLatestStationsByIds returns one deterministic latest row per station when timestamps tie`() = runBlocking {
        val fetchedAt = 1_744_947_200_000L
        val selectedBucket = CacheKey(
            latitudeBucket = 16649,
            longitudeBucket = 50811,
            radiusMeters = 3_000,
            fuelType = "DIESEL",
        )
        val laterTimestampBucket = selectedBucket.copy(
            fuelType = "GASOLINE",
        )
        val laterRadiusBucket = selectedBucket.copy(
            radiusMeters = 4_000,
        )
        val laterLatitudeBucket = selectedBucket.copy(
            latitudeBucket = 16650,
        )
        val laterLongitudeBucket = selectedBucket.copy(
            longitudeBucket = 50812,
        )

        dao.upsertAll(
            listOf(
                station(
                    cacheKey = laterTimestampBucket,
                    stationId = "station-1",
                    priceWon = 1_700,
                    fetchedAtEpochMillis = fetchedAt - 1,
                ),
                station(
                    cacheKey = laterTimestampBucket,
                    stationId = "station-1",
                    priceWon = 1_680,
                    fetchedAtEpochMillis = fetchedAt,
                ),
                station(
                    cacheKey = laterRadiusBucket,
                    stationId = "station-1",
                    priceWon = 1_650,
                    fetchedAtEpochMillis = fetchedAt,
                ),
                station(
                    cacheKey = laterLatitudeBucket,
                    stationId = "station-1",
                    priceWon = 1_640,
                    fetchedAtEpochMillis = fetchedAt,
                ),
                station(
                    cacheKey = laterLongitudeBucket,
                    stationId = "station-1",
                    priceWon = 1_630,
                    fetchedAtEpochMillis = fetchedAt,
                ),
                station(
                    cacheKey = selectedBucket,
                    stationId = "station-1",
                    priceWon = 1_620,
                    fetchedAtEpochMillis = fetchedAt,
                ),
            ),
        )

        val rows = dao.observeLatestStationsByIds(listOf("station-1")).first()

        assertEquals(listOf(1_620), rows.map { it.priceWon })
    }

    @Test
    fun `observeLatestStationsByIds returns one latest row for each requested station`() = runBlocking {
        val olderBucket = CacheKey(
            latitudeBucket = 16649,
            longitudeBucket = 50811,
            radiusMeters = 3_000,
            fuelType = "GASOLINE",
        )
        val newerBucket = olderBucket.copy(latitudeBucket = olderBucket.latitudeBucket + 1)
        val olderFetchedAt = 1_744_947_200_000L
        val newerFetchedAt = olderFetchedAt + 60_000L

        dao.upsertAll(
            listOf(
                station(
                    cacheKey = olderBucket,
                    stationId = "station-1",
                    priceWon = 1_700,
                    fetchedAtEpochMillis = olderFetchedAt,
                ),
                station(
                    cacheKey = newerBucket,
                    stationId = "station-1",
                    priceWon = 1_690,
                    fetchedAtEpochMillis = newerFetchedAt,
                ),
                station(
                    cacheKey = olderBucket,
                    stationId = "station-2",
                    priceWon = 1_680,
                    fetchedAtEpochMillis = olderFetchedAt,
                ),
                station(
                    cacheKey = newerBucket,
                    stationId = "station-2",
                    priceWon = 1_670,
                    fetchedAtEpochMillis = newerFetchedAt,
                ),
            ),
        )

        val rows = dao.observeLatestStationsByIds(listOf("station-2", "station-1")).first()

        assertEquals(listOf("station-1", "station-2"), rows.map { it.stationId })
        assertEquals(listOf(1_690, 1_670), rows.map { it.priceWon })
    }

    @Test
    fun `replaceSnapshot records empty snapshot metadata`() = runBlocking {
        val cacheKey = CacheKey(
            latitudeBucket = 16649,
            longitudeBucket = 50811,
            radiusMeters = 3_000,
            fuelType = "GASOLINE",
        )

        dao.replaceSnapshot(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType,
            fetchedAtEpochMillis = 1_744_947_200_000L,
            entities = emptyList(),
        )

        assertEquals(
            StationCacheSnapshotEntity(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType,
                fetchedAtEpochMillis = 1_744_947_200_000L,
            ),
            dao.observeSnapshot(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType,
            ).first(),
        )
        assertEquals(
            emptyList<StationCacheEntity>(),
            dao.observeStations(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType,
            ).first(),
        )
    }

    @Test
    fun `pruneOlderThan preserves current station rows and empty snapshot markers`() = runBlocking {
        val oldKey = CacheKey(
            latitudeBucket = 16649,
            longitudeBucket = 50811,
            radiusMeters = 3_000,
            fuelType = "GASOLINE",
        )
        val currentKey = oldKey.copy(latitudeBucket = oldKey.latitudeBucket + 1)
        val emptyCurrentKey = oldKey.copy(latitudeBucket = oldKey.latitudeBucket + 2)
        val cutoff = 1_744_947_200_000L

        dao.replaceSnapshot(
            latitudeBucket = oldKey.latitudeBucket,
            longitudeBucket = oldKey.longitudeBucket,
            radiusMeters = oldKey.radiusMeters,
            fuelType = oldKey.fuelType,
            fetchedAtEpochMillis = cutoff - 1,
            entities = listOf(station(oldKey, stationId = "old-station", fetchedAtEpochMillis = cutoff - 1)),
        )
        dao.replaceSnapshot(
            latitudeBucket = currentKey.latitudeBucket,
            longitudeBucket = currentKey.longitudeBucket,
            radiusMeters = currentKey.radiusMeters,
            fuelType = currentKey.fuelType,
            fetchedAtEpochMillis = cutoff,
            entities = listOf(station(currentKey, stationId = "current-station", fetchedAtEpochMillis = cutoff)),
        )
        dao.replaceSnapshot(
            latitudeBucket = emptyCurrentKey.latitudeBucket,
            longitudeBucket = emptyCurrentKey.longitudeBucket,
            radiusMeters = emptyCurrentKey.radiusMeters,
            fuelType = emptyCurrentKey.fuelType,
            fetchedAtEpochMillis = cutoff,
            entities = emptyList(),
        )

        dao.pruneOlderThan(cutoff)

        assertEquals(emptyList<StationCacheEntity>(), dao.stationsFor(oldKey))
        assertEquals(null, dao.snapshotFor(oldKey))
        assertEquals(listOf("current-station"), dao.stationsFor(currentKey).map { it.stationId })
        assertEquals(cutoff, dao.snapshotFor(currentKey)?.fetchedAtEpochMillis)
        assertEquals(emptyList<StationCacheEntity>(), dao.stationsFor(emptyCurrentKey))
        assertEquals(cutoff, dao.snapshotFor(emptyCurrentKey)?.fetchedAtEpochMillis)
    }

    private fun station(
        cacheKey: CacheKey,
        stationId: String,
        priceWon: Int = 1_699,
        fetchedAtEpochMillis: Long = 1_744_947_200_000,
    ) = StationCacheEntity(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType,
        stationId = stationId,
        brandCode = "GSC",
        name = "Station $stationId",
        priceWon = priceWon,
        latitude = 37.498095,
        longitude = 127.027610,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )

    private suspend fun StationCacheDao.stationsFor(cacheKey: CacheKey): List<StationCacheEntity> =
        observeStations(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType,
        ).first()

    private suspend fun StationCacheDao.snapshotFor(cacheKey: CacheKey): StationCacheSnapshotEntity? =
        observeSnapshot(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType,
        ).first()

    private data class CacheKey(
        val latitudeBucket: Int,
        val longitudeBucket: Int,
        val radiusMeters: Int,
        val fuelType: String,
    )
}
