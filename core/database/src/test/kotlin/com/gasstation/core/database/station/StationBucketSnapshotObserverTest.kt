package com.gasstation.core.database.station

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.GasStationDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationBucketSnapshotObserverTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: StationCacheDao
    private lateinit var observer: StationBucketSnapshotObserver

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.stationCacheDao()
        observer = RoomStationBucketSnapshotObserver(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `nonempty replacement emits marker and rows from one snapshot`() = runBlocking {
        val emissions = collectSnapshots(count = 2) {
            dao.replaceSnapshot(
                cacheKey = CACHE_KEY,
                fetchedAtEpochMillis = NEWER_FETCHED_AT,
                entities = listOf(station("station-1", NEWER_FETCHED_AT)),
            )
        }

        assertNull(emissions[0].marker)
        assertTrue(emissions[0].rows.isEmpty())
        assertEquals(NEWER_FETCHED_AT, emissions[1].marker?.fetchedAtEpochMillis)
        assertEquals(listOf("station-1"), emissions[1].rows.map { it.stationId })
        assertTimestampInvariant(emissions)
    }

    @Test
    fun `empty replacement preserves successful empty snapshot marker`() = runBlocking {
        dao.replaceSnapshot(
            cacheKey = CACHE_KEY,
            fetchedAtEpochMillis = OLDER_FETCHED_AT,
            entities = listOf(station("station-1", OLDER_FETCHED_AT)),
        )

        val emissions = collectSnapshots(count = 2) {
            dao.replaceSnapshot(
                cacheKey = CACHE_KEY,
                fetchedAtEpochMillis = NEWER_FETCHED_AT,
                entities = emptyList(),
            )
        }

        assertEquals(OLDER_FETCHED_AT, emissions[0].marker?.fetchedAtEpochMillis)
        assertEquals(listOf("station-1"), emissions[0].rows.map { it.stationId })
        assertEquals(NEWER_FETCHED_AT, emissions[1].marker?.fetchedAtEpochMillis)
        assertTrue(emissions[1].rows.isEmpty())
        assertTimestampInvariant(emissions)
    }

    @Test
    fun `repeated replacements never emit rows from a different marker timestamp`() = runBlocking {
        val snapshots = Channel<StationBucketSnapshot>(Channel.UNLIMITED)
        val collection = launch {
            observer.observe(
                latitudeBucket = CACHE_KEY.latitudeBucket,
                longitudeBucket = CACHE_KEY.longitudeBucket,
                radiusMeters = CACHE_KEY.radiusMeters,
                fuelType = CACHE_KEY.fuelType,
            ).collect(snapshots::send)
        }
        val emissions = mutableListOf(withTimeout(5_000) { snapshots.receive() })

        listOf(
            OLDER_FETCHED_AT to listOf(station("station-old", OLDER_FETCHED_AT)),
            NEWER_FETCHED_AT to listOf(
                station("station-new-1", NEWER_FETCHED_AT),
                station("station-new-2", NEWER_FETCHED_AT),
            ),
            NEWEST_FETCHED_AT to listOf(station("station-newest", NEWEST_FETCHED_AT)),
        ).forEach { (fetchedAt, rows) ->
            dao.replaceSnapshot(
                cacheKey = CACHE_KEY,
                fetchedAtEpochMillis = fetchedAt,
                entities = rows,
            )
        }
        withTimeout(5_000) {
            while (emissions.last().marker?.fetchedAtEpochMillis != NEWEST_FETCHED_AT) {
                emissions += snapshots.receive()
            }
        }
        collection.cancelAndJoin()

        assertEquals(
            listOf("station-newest"),
            emissions.last().rows.map { it.stationId },
        )
        assertTimestampInvariant(emissions)
    }

    @Test
    fun `timestamp mismatch fails deterministically`() = runBlocking {
        dao.upsertSnapshot(
            StationCacheSnapshotEntity(
                latitudeBucket = CACHE_KEY.latitudeBucket,
                longitudeBucket = CACHE_KEY.longitudeBucket,
                radiusMeters = CACHE_KEY.radiusMeters,
                fuelType = CACHE_KEY.fuelType,
                fetchedAtEpochMillis = NEWER_FETCHED_AT,
            ),
        )
        dao.upsertAll(listOf(station("station-old", OLDER_FETCHED_AT)))

        val failure = runCatching {
            observer.observe(
                latitudeBucket = CACHE_KEY.latitudeBucket,
                longitudeBucket = CACHE_KEY.longitudeBucket,
                radiusMeters = CACHE_KEY.radiusMeters,
                fuelType = CACHE_KEY.fuelType,
            ).first()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(
            "Station cache rows must share the bucket snapshot timestamp",
            failure?.message,
        )
    }

    @Test
    fun `missing marker normalizes orphan rows to empty`() = runBlocking {
        dao.upsertAll(listOf(station("orphan", OLDER_FETCHED_AT)))

        val snapshot = observer.observe(
            latitudeBucket = CACHE_KEY.latitudeBucket,
            longitudeBucket = CACHE_KEY.longitudeBucket,
            radiusMeters = CACHE_KEY.radiusMeters,
            fuelType = CACHE_KEY.fuelType,
        ).first()

        assertNull(snapshot.marker)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `unrelated bucket invalidation is suppressed and collection cancels cleanly`() = runBlocking {
        val snapshots = Channel<StationBucketSnapshot>(Channel.UNLIMITED)
        val collection = launch {
            observer.observe(
                latitudeBucket = CACHE_KEY.latitudeBucket,
                longitudeBucket = CACHE_KEY.longitudeBucket,
                radiusMeters = CACHE_KEY.radiusMeters,
                fuelType = CACHE_KEY.fuelType,
            ).collect(snapshots::send)
        }
        withTimeout(5_000) { snapshots.receive() }

        val otherKey = CACHE_KEY.copy(latitudeBucket = CACHE_KEY.latitudeBucket + 1)
        dao.replaceSnapshot(
            cacheKey = otherKey,
            fetchedAtEpochMillis = NEWER_FETCHED_AT,
            entities = listOf(station(otherKey, "unrelated", NEWER_FETCHED_AT)),
        )

        assertNull(withTimeoutOrNull(500) { snapshots.receive() })
        collection.cancelAndJoin()

        dao.replaceSnapshot(
            cacheKey = CACHE_KEY,
            fetchedAtEpochMillis = NEWEST_FETCHED_AT,
            entities = listOf(station("after-cancel", NEWEST_FETCHED_AT)),
        )
        assertNull(withTimeoutOrNull(500) { snapshots.receive() })
    }

    @Test
    fun `pruning normalizes removed marker to empty rows`() = runBlocking {
        dao.replaceSnapshot(
            cacheKey = CACHE_KEY,
            fetchedAtEpochMillis = OLDER_FETCHED_AT,
            entities = listOf(station("station-old", OLDER_FETCHED_AT)),
        )

        val emissions = collectSnapshots(count = 2) {
            dao.pruneOlderThan(NEWER_FETCHED_AT)
        }

        assertEquals(OLDER_FETCHED_AT, emissions[0].marker?.fetchedAtEpochMillis)
        assertEquals(listOf("station-old"), emissions[0].rows.map { it.stationId })
        assertNull(emissions[1].marker)
        assertTrue(emissions[1].rows.isEmpty())
        assertTimestampInvariant(emissions)
    }

    private suspend fun collectSnapshots(count: Int, mutate: suspend () -> Unit): List<StationBucketSnapshot> = coroutineScope {
        val emissions = mutableListOf<StationBucketSnapshot>()
        val initialEmissionReceived = CompletableDeferred<Unit>()
        val collection = launch {
            withTimeout(5_000) {
                observer.observe(
                    latitudeBucket = CACHE_KEY.latitudeBucket,
                    longitudeBucket = CACHE_KEY.longitudeBucket,
                    radiusMeters = CACHE_KEY.radiusMeters,
                    fuelType = CACHE_KEY.fuelType,
                )
                    .take(count)
                    .collect { snapshot ->
                        emissions += snapshot
                        initialEmissionReceived.complete(Unit)
                    }
            }
        }
        initialEmissionReceived.await()
        mutate()
        collection.join()
        emissions
    }

    private fun assertTimestampInvariant(snapshots: List<StationBucketSnapshot>) {
        snapshots.forEach { snapshot ->
            assertTrue(
                snapshot.rows.all { row ->
                    row.fetchedAtEpochMillis == snapshot.marker?.fetchedAtEpochMillis
                },
            )
        }
    }

    private fun station(stationId: String, fetchedAtEpochMillis: Long) = station(
        cacheKey = CACHE_KEY,
        stationId = stationId,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )

    private fun station(cacheKey: CacheKey, stationId: String, fetchedAtEpochMillis: Long) = StationCacheEntity(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType,
        stationId = stationId,
        brandCode = "GSC",
        name = "Station $stationId",
        priceWon = 1_699,
        latitude = 37.498095,
        longitude = 127.027610,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )

    private suspend fun StationCacheDao.replaceSnapshot(
        cacheKey: CacheKey,
        fetchedAtEpochMillis: Long,
        entities: List<StationCacheEntity>,
    ) = replaceSnapshot(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        entities = entities,
    )

    private data class CacheKey(val latitudeBucket: Int, val longitudeBucket: Int, val radiusMeters: Int, val fuelType: String)

    private companion object {
        val CACHE_KEY = CacheKey(
            latitudeBucket = 16_649,
            longitudeBucket = 50_811,
            radiusMeters = 3_000,
            fuelType = "GASOLINE",
        )
        const val OLDER_FETCHED_AT = 1_744_947_200_000L
        const val NEWER_FETCHED_AT = OLDER_FETCHED_AT + 60_000L
        const val NEWEST_FETCHED_AT = NEWER_FETCHED_AT + 60_000L
    }
}
