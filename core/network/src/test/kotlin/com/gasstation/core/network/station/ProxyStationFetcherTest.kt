package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.model.ProxyStationSearchRequestDto
import com.gasstation.core.network.model.ProxyStationSearchResponseDto
import com.gasstation.core.network.service.ProxyStationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import com.google.gson.JsonParseException as GsonJsonParseException
import com.google.gson.stream.MalformedJsonException as GsonMalformedJsonException

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
    fun `fetchStations skips out-of-range coordinates and preserves valid rows`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stations":[
                      {"stationId":"station-1","name":"강남주유소","brandCode":"SKG","fuelType":"GASOLINE","priceWon":1689,"latitude":37.4987,"longitude":127.0285},
                      {"stationId":"station-2","name":"범위초과주유소","brandCode":"GSC","fuelType":"GASOLINE","priceWon":1669,"latitude":200.0,"longitude":127.0290}
                    ]}
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

            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals(listOf("station-1"), stations.map { it.stationId })
            assertEquals(Coordinates(latitude = 37.4987, longitude = 127.0285), stations.single().coordinates)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations returns failure when only out-of-range coordinates remain`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stations":[{"stationId":"station-2","name":"범위초과주유소","brandCode":"GSC","fuelType":"GASOLINE","priceWon":1669,"latitude":200.0,"longitude":127.0290}]}
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

            assertEquals(
                NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload),
                result,
            )
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

            assertEquals(
                NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload),
                result,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations keeps only proxy rows matching the requested fuel`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stations":[
                      {"stationId":"gasoline","name":"휘발유 주유소","brandCode":"SKG","fuelType":"GASOLINE","priceWon":1689,"latitude":37.4987,"longitude":127.0285},
                      {"stationId":"diesel","name":"경유 주유소","brandCode":"GSC","fuelType":"DIESEL","priceWon":1599,"latitude":37.4990,"longitude":127.0290}
                    ]}
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val result = proxyFetcher(server).fetchStations(
                origin = TEST_ORIGIN,
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            val success = result as NetworkStationFetchResult.Success
            assertEquals(listOf("gasoline"), success.stations.map { it.stationId })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations rejects proxy payload when every row has a different fuel`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stations":[
                      {"stationId":"diesel","name":"경유 주유소","brandCode":"GSC","fuelType":"DIESEL","priceWon":1599,"latitude":37.4990,"longitude":127.0290}
                    ]}
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val result = proxyFetcher(server).fetchStations(
                origin = TEST_ORIGIN,
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            assertEquals(
                NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload),
                result,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations classifies proxy HTTP statuses and preserves the Retrofit cause`() = runBlocking {
        listOf(408, 429, 500, 404).forEach { statusCode ->
            val server = MockWebServer()
            val response = MockResponse()
                .setResponseCode(statusCode)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error":"status-$statusCode"}""")
            server.enqueue(response)
            server.start()

            try {
                val result = proxyFetcher(server).fetchStations(
                    origin = TEST_ORIGIN,
                    radius = SearchRadius.KM_3,
                    fuelType = FuelType.GASOLINE,
                )

                val failure = result as NetworkStationFetchResult.Failure
                assertEquals(NetworkStationFailure.Http(statusCode), failure.reason)
                val cause = failure.cause as HttpException
                assertEquals(statusCode, cause.code())
                assertEquals(1, server.requestCount)
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun `fetchStations classifies malformed proxy body and preserves the parsing cause`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("not-json"),
        )
        server.start()

        try {
            val result = proxyFetcher(server).fetchStations(
                origin = TEST_ORIGIN,
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            val failure = result as NetworkStationFetchResult.Failure
            assertEquals(NetworkStationFailure.InvalidPayload, failure.reason)
            assertTrue(failure.cause.hasGsonParsingCause())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchStations classifies proxy timeout network and unknown failures with the original cause`() = runBlocking {
        val cases = listOf(
            InterruptedIOException("slow") to NetworkStationFailure.Timeout,
            IOException("offline") to NetworkStationFailure.Network,
            IllegalStateException("unexpected") to NetworkStationFailure.Unknown,
        )

        cases.forEach { (expectedCause, expectedReason) ->
            val result = ProxyStationFetcher(ThrowingProxyStationService(expectedCause)).fetchStations(
                origin = TEST_ORIGIN,
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            val failure = result as NetworkStationFetchResult.Failure
            assertEquals(expectedReason, failure.reason)
            assertSame(expectedCause, failure.cause)
        }
    }

    @Test
    fun `fetchStations classifies nested genuine Gson parse failure from proxy as invalid payload`() = runBlocking {
        val cause = IOException("converter failed", GsonJsonParseException("invalid payload"))

        val result = ProxyStationFetcher(ThrowingProxyStationService(cause)).fetchStations(
            origin = TEST_ORIGIN,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
        )

        val failure = result as NetworkStationFetchResult.Failure
        assertEquals(NetworkStationFailure.InvalidPayload, failure.reason)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `fetchStations keeps unrelated same-simple-name proxy cause classified as network`() = runBlocking {
        val cause = IOException("offline", JsonParseException("not Gson"))

        val result = ProxyStationFetcher(ThrowingProxyStationService(cause)).fetchStations(
            origin = TEST_ORIGIN,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
        )

        val failure = result as NetworkStationFetchResult.Failure
        assertEquals(NetworkStationFailure.Network, failure.reason)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `fetchStations rethrows proxy cancellation unchanged`() {
        val cancellation = CancellationException("cancelled")
        val fetcher = ProxyStationFetcher(ThrowingProxyStationService(cancellation))

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                fetcher.fetchStations(
                    origin = TEST_ORIGIN,
                    radius = SearchRadius.KM_3,
                    fuelType = FuelType.GASOLINE,
                )
            }
        }

        assertSame(cancellation, thrown)
    }

    private fun proxyFetcher(server: MockWebServer) = ProxyStationFetcher(
        proxyStationService = NetworkModule.provideProxyStationService(server.url("/").toString()),
    )

    private class ThrowingProxyStationService(private val throwable: Throwable) : ProxyStationService {
        override suspend fun findStations(request: ProxyStationSearchRequestDto): ProxyStationSearchResponseDto = throw throwable
    }

    private class JsonParseException(message: String) : RuntimeException(message)

    private companion object {
        val TEST_ORIGIN = Coordinates(latitude = 37.497927, longitude = 127.027583)
    }
}

private fun Throwable?.hasGsonParsingCause(): Boolean = generateSequence(this) { it.cause }
    .any { it is GsonJsonParseException || it is GsonMalformedJsonException }
