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
                    fuelType = "GASOLINE",
                    priceWon = 1_600 + index,
                    fetchedAtEpochMillis = 1_744_947_200_000L + index,
                ),
            )
        }

        dao.keepLatestTenByStationAndFuelType(
            stationId = "station-1",
            fuelType = "GASOLINE",
        )

        val rows = dao.observeByStationIdsAndFuelType(
            stationIds = listOf("station-1"),
            fuelType = "GASOLINE",
        ).first()

        assertEquals((1_611 downTo 1_602).toList(), rows.map { it.priceWon })
    }

    @Test
    fun `history queries and trimming stay within the requested fuel type`() = runBlocking {
        repeat(12) { index ->
            dao.insert(
                StationPriceHistoryEntity(
                    stationId = "station-1",
                    fuelType = "GASOLINE",
                    priceWon = 1_700 + index,
                    fetchedAtEpochMillis = 1_744_947_200_000L + index,
                ),
            )
        }
        repeat(2) { index ->
            dao.insert(
                StationPriceHistoryEntity(
                    stationId = "station-1",
                    fuelType = "DIESEL",
                    priceWon = 1_500 + index,
                    fetchedAtEpochMillis = 1_744_948_200_000L + index,
                ),
            )
        }

        dao.keepLatestTenByStationAndFuelType(
            stationId = "station-1",
            fuelType = "GASOLINE",
        )

        val gasolineRows = dao.observeByStationIdsAndFuelType(
            stationIds = listOf("station-1"),
            fuelType = "GASOLINE",
        ).first()
        val allRows = dao.observeByStationIds(listOf("station-1")).first()

        assertEquals(10, gasolineRows.size)
        assertEquals(listOf("DIESEL", "DIESEL"), allRows.take(2).map { it.fuelType })
        assertEquals(2, allRows.count { it.fuelType == "DIESEL" })
        assertEquals(10, allRows.count { it.fuelType == "GASOLINE" })
    }
}
