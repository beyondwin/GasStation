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
class StationPriceHistoryDaoTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: StationPriceHistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.stationPriceHistoryDao()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `keepLatestTenByStation removes older rows`() = runBlocking {
        repeat(12) { index ->
            dao.insert(
                StationPriceHistoryEntity(
                    stationId = "station-1",
                    priceWon = 1_600 + index,
                    fetchedAtEpochMillis = 1_744_947_200_000L + index,
                ),
            )
        }

        dao.keepLatestTenByStation("station-1")

        val rows = dao.observeByStationIds(listOf("station-1")).first()

        assertEquals((1_611 downTo 1_602).toList(), rows.map { it.priceWon })
    }
}
