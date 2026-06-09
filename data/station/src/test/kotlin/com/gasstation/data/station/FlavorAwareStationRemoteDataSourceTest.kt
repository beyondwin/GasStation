package com.gasstation.data.station

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Optional

class FlavorAwareStationRemoteDataSourceTest {

    @Test
    fun `fetchStations delegates to seed source when demo seed is bound`() = runTest {
        val prod = RecordingDefaultStationRemoteDataSource(
            RemoteStationFetchResult.Failure(StationRefreshFailureReason.Network),
        )
        val seed = RecordingSeedStationRemoteDataSource(
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
        )
        val source = FlavorAwareStationRemoteDataSource(
            prodRemoteDataSource = prod,
            seedRemoteDataSource = Optional.of(seed),
        )

        val result = source.fetchStations(stationQuery())

        assertEquals(0, prod.calls)
        assertEquals(1, seed.calls)
        assertEquals(
            listOf("seed-station"),
            (result as RemoteStationFetchResult.Success).stations.map { it.stationId },
        )
    }

    @Test
    fun `fetchStations delegates to prod source when seed source is absent`() = runTest {
        val prod = RecordingDefaultStationRemoteDataSource(
            RemoteStationFetchResult.Success(
                listOf(
                    RemoteStation(
                        stationId = "prod-station",
                        name = "Prod Station",
                        brandCode = "GSC",
                        priceWon = 1_699,
                        coordinates = Coordinates(37.498095, 127.027610),
                    ),
                ),
            ),
        )
        val source = FlavorAwareStationRemoteDataSource(
            prodRemoteDataSource = prod,
            seedRemoteDataSource = Optional.empty(),
        )

        val result = source.fetchStations(stationQuery())

        assertEquals(1, prod.calls)
        assertEquals(
            listOf("prod-station"),
            (result as RemoteStationFetchResult.Success).stations.map { it.stationId },
        )
    }

    private fun stationQuery() = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )
}

private class RecordingDefaultStationRemoteDataSource(private val result: RemoteStationFetchResult) :
    StationRemoteDataSource {
    var calls = 0
        private set

    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        calls += 1
        return result
    }
}

private class RecordingSeedStationRemoteDataSource(private val result: RemoteStationFetchResult) :
    SeedStationRemoteDataSource {
    var calls = 0
        private set

    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        calls += 1
        return result
    }
}
