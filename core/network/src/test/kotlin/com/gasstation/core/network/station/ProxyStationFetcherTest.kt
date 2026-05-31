package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStationFetcherTest {
    @Test
    fun `fetchStations posts Android-ready query and maps proxy stations`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "stations": [
                        {
                          "stationId": "station-1",
                          "name": "강남주유소",
                          "brandCode": "SKG",
                          "fuelType": "GASOLINE",
                          "priceWon": 1689,
                          "latitude": 37.4987,
                          "longitude": 127.0285,
                          "fetchedAtEpochMillis": 1776501938392
                        }
                      ],
                      "fetchedAtEpochMillis": 1776501938392
                    }
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val fetcher = ProxyStationFetcher(
                proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            val request = requireNotNull(server.takeRequest())
            assertEquals("/v1/stations/nearby", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"latitude\":37.497927"))
            assertTrue(body.contains("\"longitude\":127.027583"))
            assertTrue(body.contains("\"radiusMeters\":3000"))
            assertTrue(body.contains("\"fuelType\":\"GASOLINE\""))

            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals("station-1", stations.single().stationId)
            assertEquals("강남주유소", stations.single().name)
            assertEquals("SKG", stations.single().brandCode)
            assertEquals(1689, stations.single().priceWon)
            assertEquals(Coordinates(latitude = 37.4987, longitude = 127.0285), stations.single().coordinates)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations returns empty success for empty proxy station list`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"stations":[],"fetchedAtEpochMillis":1776501938392}"""),
        )
        server.start()

        try {
            val fetcher = ProxyStationFetcher(
                proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.DIESEL,
            )

            assertEquals(NetworkStationFetchResult.Success(emptyList()), result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations returns failure when proxy station payload is incomplete`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stations":[{"stationId":"station-1","name":"","brandCode":"SKG","fuelType":"GASOLINE","priceWon":1689,"latitude":37.4987,"longitude":127.0285}]}
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val fetcher = ProxyStationFetcher(
                proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            assertEquals(NetworkStationFetchResult.Failure, result)
        } finally {
            server.shutdown()
        }
    }
}
