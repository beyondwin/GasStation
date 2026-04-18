package com.gasstation.core.network.di

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
}
