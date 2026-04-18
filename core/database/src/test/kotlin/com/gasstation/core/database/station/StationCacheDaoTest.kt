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
            entities = listOf(initialStation),
        )
        dao.replaceSnapshot(
            latitudeBucket = otherKey.latitudeBucket,
            longitudeBucket = otherKey.longitudeBucket,
            radiusMeters = otherKey.radiusMeters,
            fuelType = otherKey.fuelType,
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
    fun `observeLatestStationsByIds returns a deterministic first row when timestamps tie`() = runBlocking {
        val fetchedAt = 1_744_947_200_000L
        val firstBucket = CacheKey(
            latitudeBucket = 16649,
            longitudeBucket = 50811,
            radiusMeters = 3_000,
            fuelType = "DIESEL",
        )
        val secondBucket = CacheKey(
            latitudeBucket = 16650,
            longitudeBucket = 50812,
            radiusMeters = 3_000,
            fuelType = "GASOLINE",
        )

        dao.upsertAll(
            listOf(
                station(
                    cacheKey = secondBucket,
                    stationId = "station-1",
                    priceWon = 1_680,
                    fetchedAtEpochMillis = fetchedAt,
                ),
                station(
                    cacheKey = firstBucket,
                    stationId = "station-1",
                    priceWon = 1_620,
                    fetchedAtEpochMillis = fetchedAt,
                ),
            ),
        )

        val rows = dao.observeLatestStationsByIds(listOf("station-1")).first()

        assertEquals(listOf("DIESEL", "GASOLINE"), rows.map { it.fuelType })
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

    private data class CacheKey(
        val latitudeBucket: Int,
        val longitudeBucket: Int,
        val radiusMeters: Int,
        val fuelType: String,
    )
}
