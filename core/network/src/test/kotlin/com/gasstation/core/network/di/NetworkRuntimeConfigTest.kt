package com.gasstation.core.network.di

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
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
    fun `proxy station service rejects blank base url before Retrofit construction`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkModule.provideProxyStationService(" ")
        }

        assertEquals(
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'.",
            error.message,
        )
    }

    @Test
    fun `proxy station service rejects invalid base url before Retrofit construction`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkModule.provideProxyStationService("not-a-url")
        }

        assertEquals(
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'.",
            error.message,
        )
    }

    @Test
    fun `proxy station service rejects path base url without trailing slash`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkModule.provideProxyStationService("https://gasstation-proxy.example/api")
        }

        assertEquals(
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'.",
            error.message,
        )
    }

    @Test
    fun `proxy station service accepts host-only and trailing-slash base urls`() {
        assertNotNull(NetworkModule.provideProxyStationService("https://gasstation-proxy.example"))
        assertNotNull(NetworkModule.provideProxyStationService("https://gasstation-proxy.example/api/"))
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
        val methodNames = NetworkModule::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name }
            .sorted()

        assertEquals(
            listOf(
                "defaultOkHttpClient",
                "provideOpinetApiKey",
                "provideOpinetBaseUrl",
                "provideOpinetService",
                "provideProxyStationService",
                "provideStationNetworkSource",
                "requireValidProxyBaseUrl",
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
