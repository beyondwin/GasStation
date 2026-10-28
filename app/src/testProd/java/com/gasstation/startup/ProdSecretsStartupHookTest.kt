package com.gasstation.startup

import android.app.Application
import com.gasstation.core.network.di.NetworkRuntimeConfig
import com.gasstation.core.network.di.StationEndpointMode
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProdSecretsStartupHookTest {
    @Test
    fun `blank local Opinet key fails fast before prod runtime starts`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            ProdSecretsStartupHook(NetworkRuntimeConfig(opinetApiKey = "")).run(Application())
        }

        assertTrue(failure.message!!.contains("opinet.apikey"))
    }

    @Test
    fun `direct endpoint starts with a local Opinet key`() {
        ProdSecretsStartupHook(NetworkRuntimeConfig(opinetApiKey = "test-key")).run(Application())
    }

    @Test
    fun `proxy endpoint starts without an embedded Opinet key`() {
        ProdSecretsStartupHook(
            NetworkRuntimeConfig(
                opinetApiKey = "",
                stationEndpointMode = StationEndpointMode.Proxy,
                stationBaseUrl = "https://proxy.example/",
            ),
        ).run(Application())
    }
}
