package com.gasstation.core.database.station

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.GasStationDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `observeWatchedStationIds returns newest first and updates order after upsert`() = runBlocking {
        dao.upsert(
            WatchedStationEntity(
                stationId = "station-1",
                name = "Gangnam First",
                brandCode = "GSC",
                latitude = 37.498095,
                longitude = 127.027610,
                watchedAtEpochMillis = 1_744_947_200_000L,
            ),
        )
        dao.upsert(
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

        dao.upsert(
            WatchedStationEntity(
                stationId = "station-1",
                name = "Gangnam First Updated",
                brandCode = "GSC",
                latitude = 37.498095,
                longitude = 127.027610,
                watchedAtEpochMillis = 1_744_947_200_200L,
            ),
        )

        assertEquals(listOf("station-1", "station-2"), dao.observeWatchedStationIds().first())

        dao.delete("station-1")

        assertEquals(listOf("station-2"), dao.observeWatchedStationIds().first())
    }
}
