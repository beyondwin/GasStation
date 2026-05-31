package com.gasstation.core.network.di

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NetworkRuntimeConfigTest {
    @Test
    fun `runtime config keeps externally provided key and station endpoint settings`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
            stationEndpointMode = StationEndpointMode.Proxy,
            stationBaseUrl = "https://gasstation-proxy.example/",
        )

        assertEquals("opinet-key", config.opinetApiKey)
        assertEquals(StationEndpointMode.Proxy, config.stationEndpointMode)
        assertEquals("https://gasstation-proxy.example/", config.stationBaseUrl)
        assertEquals(
            listOf("opinetApiKey", "stationEndpointMode", "stationBaseUrl"),
            NetworkRuntimeConfig::class.java.declaredFields.map { it.name },
        )
    }

    @Test
    fun `default runtime config uses direct Opinet`() {
        val config = NetworkRuntimeConfig(opinetApiKey = "opinet-key")

        assertEquals(StationEndpointMode.DirectOpinet, config.stationEndpointMode)
        assertEquals(NetworkModule.provideOpinetBaseUrl(), config.stationBaseUrl)
    }

    @Test
    fun `runtime config supports proxy endpoint mode`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
            stationEndpointMode = StationEndpointMode.Proxy,
            stationBaseUrl = "https://gasstation-proxy.example/",
        )

        assertEquals(StationEndpointMode.Proxy, config.stationEndpointMode)
        assertEquals("https://gasstation-proxy.example/", config.stationBaseUrl)
    }

    @Test
    fun `provideOpinetApiKey returns opinet api key from runtime config`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
        )

        val apiKey = NetworkModule.provideOpinetApiKey(config)

        assertEquals("opinet-key", apiKey)
    }

    @Test
    fun `network module exposes only opinet runtime helpers`() {
        val methodNames = NetworkModule::class.java.declaredMethods.map { it.name }.sorted()

        assertEquals(
            listOf(
                "defaultOkHttpClient",
                "provideOpinetApiKey",
                "provideOpinetBaseUrl",
                "provideOpinetService",
                "provideProxyStationService",
                "provideStationNetworkSource",
            ),
            methodNames,
        )
    }

    @Test
    fun `default okhttp client applies bounded timeout policy`() {
        val factory = NetworkModule::class.java.getDeclaredMethod("defaultOkHttpClient").apply {
            isAccessible = true
        }

        val client = factory.invoke(NetworkModule) as OkHttpClient

        assertEquals(8_000, client.callTimeoutMillis.toLong())
        assertEquals(4_000, client.connectTimeoutMillis.toLong())
        assertEquals(8_000, client.readTimeoutMillis.toLong())
    }

    @Test
    fun `network module no longer ships kakao trans coord dto`() {
        try {
            Class.forName("com.gasstation.core.network.model.KakaoTransCoordDto")
            fail("KakaoTransCoordDto should be removed when Kakao API integration is gone")
        } catch (_: ClassNotFoundException) {
            // Expected once the unused Kakao DTO is removed.
        }
    }
}
