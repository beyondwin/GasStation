package com.gasstation.core.network.di

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkRuntimeConfigTest {
    @Test
    fun `runtime config keeps only the externally provided opinet api key`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
        )

        assertEquals("opinet-key", config.opinetApiKey)
        assertEquals(listOf("opinetApiKey"), NetworkRuntimeConfig::class.java.declaredFields.map { it.name })
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
}
