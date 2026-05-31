package com.gasstation.data.station

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.core.network.station.NetworkRemoteStation
import com.gasstation.core.network.station.NetworkStationFetchResult
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
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
    fun `socket timeout maps to timeout failure`() = runBlocking {
        val dataSource = DefaultStationRemoteDataSource(
            stationNetworkSource = ThrowingStationNetworkSource(SocketTimeoutException("slow")),
        )
        val result = dataSource.fetchStations(stationQuery())

        assertTrue(result is RemoteStationFetchResult.Failure)
        val failure = result as RemoteStationFetchResult.Failure
        assertEquals(StationRefreshFailureReason.Timeout, failure.reason)
        assertTrue(failure.cause is SocketTimeoutException)
    }

    @Test
    fun `interrupted io timeout maps to timeout failure`() = runBlocking {
        val dataSource = DefaultStationRemoteDataSource(
            stationNetworkSource = ThrowingStationNetworkSource(InterruptedIOException("call timeout")),
        )
        val result = dataSource.fetchStations(stationQuery())

        assertTrue(result is RemoteStationFetchResult.Failure)
        val failure = result as RemoteStationFetchResult.Failure
        assertEquals(StationRefreshFailureReason.Timeout, failure.reason)
        assertTrue(failure.cause is InterruptedIOException)
    }

    @Test
    fun `payload parsing failure maps to invalid payload`() = runBlocking {
        val dataSource = DefaultStationRemoteDataSource(
            stationNetworkSource = ThrowingStationNetworkSource(JsonSyntaxException("malformed json")),
        )
        val result = dataSource.fetchStations(stationQuery())

        assertTrue(result is RemoteStationFetchResult.Failure)
        val failure = result as RemoteStationFetchResult.Failure
        assertEquals(StationRefreshFailureReason.InvalidPayload, failure.reason)
        assertTrue(failure.cause is JsonSyntaxException)
    }

    @Test
    fun `cancellation exception is rethrown`() {
        runBlocking {
            val dataSource = DefaultStationRemoteDataSource(
                stationNetworkSource = ThrowingStationNetworkSource(CancellationException("cancelled")),
            )

            assertThrows(CancellationException::class.java) {
                runBlocking {
                    dataSource.fetchStations(stationQuery())
                }
            }
        }
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
        override suspend fun fetchStations(
            origin: Coordinates,
            radius: SearchRadius,
            fuelType: FuelType,
        ): NetworkStationFetchResult = result
    }

    private class ThrowingStationNetworkSource(private val throwable: Throwable) :
        com.gasstation.core.network.station.StationNetworkSource {
        override suspend fun fetchStations(
            origin: Coordinates,
            radius: SearchRadius,
            fuelType: FuelType,
        ): NetworkStationFetchResult {
            throw throwable
        }
    }

    private class JsonSyntaxException(message: String) : RuntimeException(message)
}
