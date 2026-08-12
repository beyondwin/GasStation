package com.gasstation.data.station

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.core.network.station.ProxyStationFetcher
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class StationTransportRetryIntegrationTest {
    @Test
    fun `direct persistent HTTP 408 is requested exactly twice by the application retry owner`() = runTest {
        val server = persistentRequestTimeoutServer()

        try {
            val dataSource = DefaultStationRemoteDataSource(
                NetworkStationFetcher(
                    opinetService = NetworkModule.provideOpinetService(server.url("/").toString()),
                    opinetApiKey = "opinet-key",
                ),
            )
            val attemptTimes = mutableListOf<Long>()

            val failure = assertFailsWith<StationRefreshException> {
                StationRetryPolicy(NoOpStationEventLogger).withRetry {
                    attemptTimes += testScheduler.currentTime
                    dataSource.fetchStations(stationQuery()).getOrThrow()
                }
            }

            assertEquals(StationRefreshFailureReason.Http(408), failure.reason)
            assertEquals(listOf(0L, 500L), attemptTimes)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `proxy persistent HTTP 408 is requested exactly twice by the application retry owner`() = runTest {
        val server = persistentRequestTimeoutServer()

        try {
            val dataSource = DefaultStationRemoteDataSource(
                ProxyStationFetcher(
                    proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
                ),
            )
            val attemptTimes = mutableListOf<Long>()

            val failure = assertFailsWith<StationRefreshException> {
                StationRetryPolicy(NoOpStationEventLogger).withRetry {
                    attemptTimes += testScheduler.currentTime
                    dataSource.fetchStations(stationQuery()).getOrThrow()
                }
            }

            assertEquals(StationRefreshFailureReason.Http(408), failure.reason)
            assertEquals(listOf(0L, 500L), attemptTimes)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun persistentRequestTimeoutServer() = MockWebServer().apply {
        repeat(4) {
            enqueue(
                MockResponse()
                    .setResponseCode(408)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"error":"request-timeout"}"""),
            )
        }
        start()
    }

    private fun stationQuery() = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )

    private fun RemoteStationFetchResult.getOrThrow(): List<RemoteStation> = when (this) {
        is RemoteStationFetchResult.Success -> stations
        is RemoteStationFetchResult.Failure -> throw StationRefreshException(reason, cause)
    }

    private data object NoOpStationEventLogger : StationEventLogger {
        override fun log(event: StationEvent) = Unit
    }
}
