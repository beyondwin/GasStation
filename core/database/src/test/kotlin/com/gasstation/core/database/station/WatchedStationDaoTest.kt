package com.gasstation.core.database.station

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.GasStationDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchedStationDaoTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: WatchedStationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.watchedStationDao()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `repeated insert preserves original watched row and order`() = runBlocking {
        val firstInsert = dao.insertIfAbsent(
            WatchedStationEntity(
                stationId = "station-1",
                name = "Gangnam First",
                brandCode = "GSC",
                latitude = 37.498095,
                longitude = 127.027610,
                watchedAtEpochMillis = 1_744_947_200_000L,
            ),
        )
        dao.insertIfAbsent(
            WatchedStationEntity(
                stationId = "station-2",
                name = "Gangnam Second",
                brandCode = "SK",
                latitude = 37.499000,
                longitude = 127.028000,
                watchedAtEpochMillis = 1_744_947_200_100L,
            ),
        )

        assertEquals(listOf("station-2", "station-1"), dao.observeWatchedStationIds().first())

        val repeatedInsert = dao.insertIfAbsent(
            WatchedStationEntity(
                stationId = "station-1",
                name = "Gangnam First Updated",
                brandCode = "GSC",
                latitude = 37.498095,
                longitude = 127.027610,
                watchedAtEpochMillis = 1_744_947_200_200L,
            ),
        )

        assertEquals(-1L, repeatedInsert)
        assertEquals(listOf("station-2", "station-1"), dao.observeWatchedStationIds().first())
        assertEquals(
            1_744_947_200_000L,
            dao.observeWatchedStations().first().single { it.stationId == "station-1" }.watchedAtEpochMillis,
        )
        assertEquals("Gangnam First", dao.observeWatchedStations().first().single { it.stationId == "station-1" }.name)
        assertTrue(firstInsert > 0L)

        dao.delete("station-1")

        assertEquals(listOf("station-2"), dao.observeWatchedStationIds().first())
    }

    @Test
    fun `equal timestamps use station id ascending in both observations`() = runBlocking {
        listOf("station-c", "station-a", "station-b").forEach { stationId ->
            dao.insertIfAbsent(
                WatchedStationEntity(
                    stationId = stationId,
                    name = stationId,
                    brandCode = "GSC",
                    latitude = 37.498095,
                    longitude = 127.027610,
                    watchedAtEpochMillis = 1_744_947_200_000L,
                ),
            )
        }

        assertEquals(listOf("station-a", "station-b", "station-c"), dao.observeWatchedStationIds().first())
        assertEquals(
            listOf("station-a", "station-b", "station-c"),
            dao.observeWatchedStations().first().map { it.stationId },
        )
    }
}
