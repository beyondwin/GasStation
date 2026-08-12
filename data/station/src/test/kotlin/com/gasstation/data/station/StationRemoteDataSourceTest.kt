package com.gasstation.data.station

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.core.network.station.NetworkRemoteStation
import com.gasstation.core.network.station.NetworkStationFailure
import com.gasstation.core.network.station.NetworkStationFetchResult
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.SocketTimeoutException

class StationRemoteDataSourceTest {
    @Test
    fun `success maps common network source stations to remote stations`() = runBlocking {
        val dataSource = DefaultStationRemoteDataSource(
            stationNetworkSource = FakeStationNetworkSource(
                NetworkStationFetchResult.Success(
                    listOf(
                        NetworkRemoteStation(
                            stationId = "station-1",
                            name = "강남주유소",
                            brandCode = "SKG",
                            priceWon = 1689,
                            coordinates = Coordinates(37.4987, 127.0285),
                        ),
                    ),
                ),
            ),
        )

        val result = dataSource.fetchStations(stationQuery())

        assertEquals(
            RemoteStationFetchResult.Success(
                listOf(
                    RemoteStation(
                        stationId = "station-1",
                        name = "강남주유소",
                        brandCode = "SKG",
                        priceWon = 1689,
                        coordinates = Coordinates(37.4987, 127.0285),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun `network timeout failure maps to domain timeout and preserves cause`() = runBlocking {
        val cause = SocketTimeoutException("slow")
        val dataSource = DefaultStationRemoteDataSource(
            stationNetworkSource = FakeStationNetworkSource(
                NetworkStationFetchResult.Failure(
                    reason = NetworkStationFailure.Timeout,
                    cause = cause,
                ),
            ),
        )
        val result = dataSource.fetchStations(stationQuery())

        val failure = result as RemoteStationFetchResult.Failure
        assertEquals(StationRefreshFailureReason.Timeout, failure.reason)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `every network failure maps to the matching domain reason and preserves cause`() = runBlocking {
        val cases = listOf(
            NetworkStationFailure.Network to StationRefreshFailureReason.Network,
            NetworkStationFailure.InvalidPayload to StationRefreshFailureReason.InvalidPayload,
            NetworkStationFailure.Http(429) to StationRefreshFailureReason.Http(429),
            NetworkStationFailure.Unknown to StationRefreshFailureReason.Unknown,
        )

        cases.forEach { (networkReason, domainReason) ->
            val cause = IllegalStateException(networkReason.toString())
            val dataSource = DefaultStationRemoteDataSource(
                stationNetworkSource = FakeStationNetworkSource(
                    NetworkStationFetchResult.Failure(networkReason, cause),
                ),
            )
            val result = dataSource.fetchStations(stationQuery())

            val failure = result as RemoteStationFetchResult.Failure
            assertEquals(domainReason, failure.reason)
            assertSame(cause, failure.cause)
        }
    }

    @Test
    fun `network failure without a cause maps without inventing one`() = runBlocking {
        val dataSource = DefaultStationRemoteDataSource(
            stationNetworkSource = FakeStationNetworkSource(
                NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload),
            ),
        )
        val result = dataSource.fetchStations(stationQuery())

        val failure = result as RemoteStationFetchResult.Failure
        assertEquals(StationRefreshFailureReason.InvalidPayload, failure.reason)
        assertEquals(null, failure.cause)
    }

    private fun stationQuery() = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )

    private class FakeStationNetworkSource(private val result: NetworkStationFetchResult) :
        com.gasstation.core.network.station.StationNetworkSource {
        override suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult =
            result
    }
}
