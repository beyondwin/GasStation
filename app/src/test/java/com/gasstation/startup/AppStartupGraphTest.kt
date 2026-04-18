package com.gasstation.startup

import com.gasstation.BuildConfig
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppStartupGraphTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @javax.inject.Inject
    lateinit var appStartupRunner: AppStartupRunner

    @javax.inject.Inject
    lateinit var startupHooks: Set<@JvmSuppressWildcards AppStartupHook>

    @Test
    fun `graph wires the expected startup hook for the current flavor`() {
        hiltRule.inject()

        val expectedHookNames = if (BuildConfig.DEMO_MODE) {
            setOf("com.gasstation.startup.DemoSeedStartupHook")
        } else {
            setOf("com.gasstation.startup.ProdSecretsStartupHook")
        }

        assertEquals(expectedHookNames, startupHooks.mapNotNull { it::class.qualifiedName }.toSet())
    }
}
