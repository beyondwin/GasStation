package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.model.OpinetResponseDto
import com.gasstation.core.network.model.OpinetStationDto
import com.gasstation.core.network.service.OpinetService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import com.google.gson.JsonParseException as GsonJsonParseException
import com.google.gson.stream.MalformedJsonException as GsonMalformedJsonException

class NetworkStationFetcherTest {
    @Test
    fun `fetchStations preserves radius contract and maps KATEC station coordinates to WGS84 locally`() = runBlocking {
        val opinetServer = MockWebServer()
        val stationKatec = LocalKoreanCoordinateTransform.wgs84ToKtm(
            latitude = 37.4987,
            longitude = 127.0285,
        )
        opinetServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"RESULT":{"OIL":[{"UNI_ID":"station-1","OS_NM":"Gangnam","POLL_DIV_CD":"SKG","PRICE":"1689","GIS_X_COOR":"${stationKatec.x}","GIS_Y_COOR":"${stationKatec.y}"}]}}
                    """.trimIndent(),
                ),
        )
        opinetServer.start()

        try {
            val fetcher = NetworkStationFetcher(
                opinetService = NetworkModule.provideOpinetService(opinetServer.url("/").toString()),
                opinetApiKey = "opinet-key",
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            val request = requireNotNull(opinetServer.takeRequest())
            val expectedOriginKatec = LocalKoreanCoordinateTransform.wgs84ToKtm(
                latitude = 37.497927,
                longitude = 127.027583,
            )
            assertEquals("3000", request.requestUrl?.queryParameter("radius"))
            assertEquals("B027", request.requestUrl?.queryParameter("prodcd"))
            assertEquals(
                expectedOriginKatec.x,
                requireNotNull(request.requestUrl?.queryParameter("x")).toDouble(),
                0.0001,
            )
            assertEquals(
                expectedOriginKatec.y,
                requireNotNull(request.requestUrl?.queryParameter("y")).toDouble(),
                0.0001,
            )
            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals(listOf("station-1"), stations.map { it.stationId })
            assertEquals(1689, stations.single().priceWon)
            assertEquals(37.4987, stations.single().coordinates.latitude, 0.0005)
            assertEquals(127.0285, stations.single().coordinates.longitude, 0.0005)
        } finally {
            opinetServer.shutdown()
        }
    }

    @Test
    fun `fetchStations filters out stations with incomplete payloads`() = runBlocking {
        val opinetServer = MockWebServer()
        opinetServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"RESULT":{"OIL":[
                      {"UNI_ID":"station-1","OS_NM":"Gangnam","POLL_DIV_CD":"SKG","PRICE":"1689","GIS_X_COOR":"127.0250","GIS_Y_COOR":"37.4980"},
                      {"UNI_ID":"station-2","OS_NM":"","POLL_DIV_CD":"GSC","PRICE":"1669","GIS_X_COOR":"127.0260","GIS_Y_COOR":"37.4990"}
                    ]}}
                    """.trimIndent(),
                ),
        )
        opinetServer.start()

        try {
            val fetcher = NetworkStationFetcher(
                opinetService = NetworkModule.provideOpinetService(opinetServer.url("/").toString()),
                opinetApiKey = "opinet-key",
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_5,
                fuelType = FuelType.DIESEL,
            )

            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals(1, stations.size)
            assertEquals("station-1", stations.single().stationId)
            assertEquals(Coordinates(latitude = 37.4980, longitude = 127.0250), stations.single().coordinates)
        } finally {
            opinetServer.shutdown()
        }
    }

    @Test
    fun `fetchStations skips rows whose KATEC transform leaves the valid range and preserves others`() = runBlocking {
        val opinetServer = MockWebServer()
        val stationKatec = LocalKoreanCoordinateTransform.wgs84ToKtm(
            latitude = 37.4987,
            longitude = 127.0285,
        )
        opinetServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"RESULT":{"OIL":[
                      {"UNI_ID":"station-1","OS_NM":"Gangnam","POLL_DIV_CD":"SKG","PRICE":"1689","GIS_X_COOR":"${stationKatec.x}","GIS_Y_COOR":"${stationKatec.y}"},
                      {"UNI_ID":"station-2","OS_NM":"OutOfRange","POLL_DIV_CD":"GSC","PRICE":"1669","GIS_X_COOR":"5000000.0","GIS_Y_COOR":"5000000.0"}
                    ]}}
                    """.trimIndent(),
                ),
        )
        opinetServer.start()

        try {
            val fetcher = NetworkStationFetcher(
                opinetService = NetworkModule.provideOpinetService(opinetServer.url("/").toString()),
                opinetApiKey = "opinet-key",
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals(listOf("station-1"), stations.map { it.stationId })
            assertEquals(37.4987, stations.single().coordinates.latitude, 0.0005)
            assertEquals(127.0285, stations.single().coordinates.longitude, 0.0005)
        } finally {
            opinetServer.shutdown()
        }
    }

    @Test
    fun `toNetworkRemoteStation filters out non-positive prices to match proxy contract`() {
        assertNull(opinetStation(priceWon = "-1").toNetworkRemoteStation())
        assertNull(opinetStation(priceWon = "0").toNetworkRemoteStation())
        assertEquals(1689, opinetStation(priceWon = "1689").toNetworkRemoteStation()?.priceWon)
    }

    @Test
    fun `fetchStations filters out rows with non-positive prices`() = runBlocking {
        val opinetServer = MockWebServer()
        opinetServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"RESULT":{"OIL":[
                      {"UNI_ID":"station-1","OS_NM":"Gangnam","POLL_DIV_CD":"SKG","PRICE":"1689","GIS_X_COOR":"127.0250","GIS_Y_COOR":"37.4980"},
                      {"UNI_ID":"station-2","OS_NM":"Negative","POLL_DIV_CD":"GSC","PRICE":"-1","GIS_X_COOR":"127.0260","GIS_Y_COOR":"37.4990"},
                      {"UNI_ID":"station-3","OS_NM":"Zero","POLL_DIV_CD":"HDO","PRICE":"0","GIS_X_COOR":"127.0270","GIS_Y_COOR":"37.5000"}
                    ]}}
                    """.trimIndent(),
                ),
        )
        opinetServer.start()

        try {
            val fetcher = NetworkStationFetcher(
                opinetService = NetworkModule.provideOpinetService(opinetServer.url("/").toString()),
                opinetApiKey = "opinet-key",
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
            )

            assertTrue(result is NetworkStationFetchResult.Success)
            val stations = (result as NetworkStationFetchResult.Success).stations
            assertEquals(listOf("station-1"), stations.map { it.stationId })
        } finally {
            opinetServer.shutdown()
        }
    }

    private fun opinetStation(priceWon: String) = OpinetStationDto(
        stationId = "station-1",
        name = "Gangnam",
        brandCode = "SKG",
        priceWon = priceWon,
        gisX = "127.0285",
        gisY = "37.4987",
    )

    @Test
    fun `fetchStations returns failure when every raw station is filtered out`() = runBlocking {
        val opinetServer = MockWebServer()
        opinetServer.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"RESULT":{"OIL":[{"UNI_ID":"station-2","OS_NM":"","POLL_DIV_CD":"GSC","PRICE":"1669","GIS_X_COOR":"127.0260","GIS_Y_COOR":"37.4990"}]}}
                    """.trimIndent(),
                ),
        )
        opinetServer.start()

        try {
            val fetcher = NetworkStationFetcher(
                opinetService = NetworkModule.provideOpinetService(opinetServer.url("/").toString()),
                opinetApiKey = "opinet-key",
            )

            val result = fetcher.fetchStations(
                origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
                radius = SearchRadius.KM_4,
                fuelType = FuelType.LPG,
            )

            assertEquals(
                NetworkStationFetchResult.Failure(NetworkStationFailure.InvalidPayload),
                result,
            )
        } finally {
            opinetServer.shutdown()
        }
    }

    @Test
    fun `fetchStations classifies direct HTTP statuses and preserves the Retrofit cause`() = runBlocking {
        listOf(408, 429, 500, 404).forEach { statusCode ->
            val server = MockWebServer()
            val response = MockResponse()
                .setResponseCode(statusCode)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error":"status-$statusCode"}""")
            server.enqueue(response)
            server.start()

            try {
                val result = directFetcher(server).fetchStations(
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
    fun `fetchStations classifies malformed direct body and preserves the parsing cause`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("not-json"),
        )
        server.start()

        try {
            val result = directFetcher(server).fetchStations(
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
    fun `fetchStations classifies direct timeout network and unknown failures with the original cause`() = runBlocking {
        val cases = listOf(
            InterruptedIOException("slow") to NetworkStationFailure.Timeout,
            IOException("offline") to NetworkStationFailure.Network,
            IllegalStateException("unexpected") to NetworkStationFailure.Unknown,
        )

        cases.forEach { (expectedCause, expectedReason) ->
            val result = NetworkStationFetcher(
                opinetService = ThrowingOpinetService(expectedCause),
                opinetApiKey = "opinet-key",
            ).fetchStations(
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
    fun `fetchStations classifies nested genuine Gson parse failure as invalid payload`() = runBlocking {
        val cause = IOException("converter failed", GsonJsonParseException("invalid payload"))

        val result = NetworkStationFetcher(
            opinetService = ThrowingOpinetService(cause),
            opinetApiKey = "opinet-key",
        ).fetchStations(
            origin = TEST_ORIGIN,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
        )

        val failure = result as NetworkStationFetchResult.Failure
        assertEquals(NetworkStationFailure.InvalidPayload, failure.reason)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `fetchStations keeps unrelated same-simple-name cause classified as network`() = runBlocking {
        val cause = IOException("offline", JsonParseException("not Gson"))

        val result = NetworkStationFetcher(
            opinetService = ThrowingOpinetService(cause),
            opinetApiKey = "opinet-key",
        ).fetchStations(
            origin = TEST_ORIGIN,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
        )

        val failure = result as NetworkStationFetchResult.Failure
        assertEquals(NetworkStationFailure.Network, failure.reason)
        assertSame(cause, failure.cause)
    }

    @Test(timeout = 1_000L)
    fun `fetchStations completes a cyclic direct cause chain as network`() = runBlocking {
        val cause = IOException("offline")
        val cycle = IllegalStateException("cyclic cause")
        cause.initCause(cycle)
        cycle.initCause(cause)

        val result = NetworkStationFetcher(
            opinetService = ThrowingOpinetService(cause),
            opinetApiKey = "opinet-key",
        ).fetchStations(
            origin = TEST_ORIGIN,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
        )

        val failure = result as NetworkStationFetchResult.Failure
        assertEquals(NetworkStationFailure.Network, failure.reason)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `fetchStations rethrows direct cancellation unchanged`() {
        val cancellation = CancellationException("cancelled")
        val fetcher = NetworkStationFetcher(
            opinetService = ThrowingOpinetService(cancellation),
            opinetApiKey = "opinet-key",
        )

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

    private fun directFetcher(server: MockWebServer) = NetworkStationFetcher(
        opinetService = NetworkModule.provideOpinetService(server.url("/").toString()),
        opinetApiKey = "opinet-key",
    )

    private class ThrowingOpinetService(private val throwable: Throwable) : OpinetService {
        override suspend fun findStations(
            code: String,
            x: Double,
            y: Double,
            radius: Int,
            sort: String,
            fuelType: String,
            out: String,
        ): OpinetResponseDto = throw throwable
    }

    private class JsonParseException(message: String) : RuntimeException(message)

    private companion object {
        val TEST_ORIGIN = Coordinates(latitude = 37.497927, longitude = 127.027583)
    }
}

private fun Throwable?.hasGsonParsingCause(): Boolean = generateSequence(this) { it.cause }
    .any { it is GsonJsonParseException || it is GsonMalformedJsonException }
