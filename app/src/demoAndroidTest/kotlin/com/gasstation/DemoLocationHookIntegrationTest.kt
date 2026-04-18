package com.gasstation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gasstation.core.location.DemoLocationOverride
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Optional
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoLocationHookIntegrationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var demoLocationOverride: Optional<DemoLocationOverride>

    @Inject
    lateinit var foregroundLocationProvider: ForegroundLocationProvider

    @Test
    fun `demo graph wires location override into foreground provider`() = runBlocking {
        hiltRule.inject()

        assertTrue(demoLocationOverride.isPresent)
        assertEquals(
            Coordinates(latitude = 37.498095, longitude = 127.02761),
            foregroundLocationProvider.currentLocation(LocationPermissionState.PreciseGranted),
        )
        assertNull(
            foregroundLocationProvider.currentLocation(LocationPermissionState.Denied),
        )
        assertEquals(
            "com.gasstation.core.location.AndroidForegroundLocationProvider",
            foregroundLocationProvider::class.qualifiedName,
        )
    }
}
