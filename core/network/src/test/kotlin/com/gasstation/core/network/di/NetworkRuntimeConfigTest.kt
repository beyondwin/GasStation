package com.gasstation.core.network.di

import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class NetworkRuntimeConfigTest {
    @Test
    fun `runtime config keeps externally provided api keys`() {
        val config = NetworkRuntimeConfig(
            kakaoApiKey = "kakao-key",
            opinetApiKey = "opinet-key",
        )

        assertEquals("kakao-key", config.kakaoApiKey)
        assertEquals("opinet-key", config.opinetApiKey)
    }

    @Test
    fun `provideOpinetApiKey returns opinet api key from runtime config`() {
        val config = NetworkRuntimeConfig(
            kakaoApiKey = "unused-kakao-key",
            opinetApiKey = "opinet-key",
        )

        val apiKey = NetworkModule.provideOpinetApiKey(config)

        assertEquals("opinet-key", apiKey)
    }

    @Test
    fun `provideKakaoService sends authorization header from runtime config`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"documents":[{"x":127.0,"y":37.0}]}"""),
        )
        server.start()

        try {
            val config = NetworkRuntimeConfig(
                kakaoApiKey = "kakao-key",
                opinetApiKey = "unused-opinet-key",
            )
            val service = NetworkModule.provideKakaoService(server.url("/").toString(), config)

            runBlocking {
                service.transCoord(
                    x = 127.0,
                    y = 37.0,
                    inputCoord = "WGS84",
                    outputCoord = "KTM",
                )
            }

            val request = requireNotNull(server.takeRequest())
            assertEquals("KakaoAK kakao-key", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
