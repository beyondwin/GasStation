package com.gasstation.data.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.model.OpinetResponseDto
import com.gasstation.core.network.service.OpinetService
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class StationRemoteDataSourceTest {
    @Test
    fun `socket timeout maps to timeout failure`() = runBlocking {
        val dataSource = DefaultStationRemoteDataSource(
            networkStationFetcher = NetworkStationFetcher(
                opinetService = FakeOpinetService(throwable = SocketTimeoutException("slow")),
                opinetApiKey = "opinet-key",
            ),
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
            networkStationFetcher = NetworkStationFetcher(
                opinetService = FakeOpinetService(throwable = InterruptedIOException("call timeout")),
                opinetApiKey = "opinet-key",
            ),
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
            networkStationFetcher = NetworkStationFetcher(
                opinetService = FakeOpinetService(throwable = JsonSyntaxException("malformed json")),
                opinetApiKey = "opinet-key",
            ),
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
                networkStationFetcher = NetworkStationFetcher(
                    opinetService = FakeOpinetService(throwable = CancellationException("cancelled")),
                    opinetApiKey = "opinet-key",
                ),
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

    private class FakeOpinetService(
        private val response: OpinetResponseDto = OpinetResponseDto(),
        private val throwable: Throwable? = null,
    ) : OpinetService {
        override suspend fun findStations(
            code: String,
            x: Double,
            y: Double,
            radius: Int,
            sort: String,
            fuelType: String,
            out: String,
        ): OpinetResponseDto {
            throwable?.let { throw it }
            return response
        }
    }

    private class JsonSyntaxException(
        message: String,
    ) : RuntimeException(message)
}
