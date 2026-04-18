package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

            assertEquals(NetworkStationFetchResult.Failure, result)
        } finally {
            opinetServer.shutdown()
        }
    }
}
