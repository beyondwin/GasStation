package com.gasstation.startup

import android.app.Application
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
            ProdSecretsStartupHook().requireLocalSecrets(opinetApiKey = "")
        }

        assertTrue(failure.message!!.contains("opinet.apikey"))
    }
}
