package com.gasstation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gasstation.core.location.DemoLocationOverride
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.core.location.LocationLookupResult
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.gasstation.test.DeviceFailureArtifactRule
import com.gasstation.test.DevicePrSmoke
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Optional
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoLocationHookIntegrationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val failureArtifacts = DeviceFailureArtifactRule()

    @Inject
    lateinit var demoLocationOverride: Optional<DemoLocationOverride>

    @Inject
    lateinit var foregroundLocationProvider: ForegroundLocationProvider

    @DevicePrSmoke
    @Test
    fun demoGraph_wiresLocationOverrideIntoForegroundProvider() = runBlocking {
        hiltRule.inject()

        assertTrue(demoLocationOverride.isPresent)
        assertEquals(
            LocationLookupResult.Success(
                Coordinates(latitude = 37.497927, longitude = 127.027583),
            ),
            foregroundLocationProvider.currentLocation(LocationPermissionState.PreciseGranted),
        )
        assertEquals(
            LocationLookupResult.PermissionDenied,
            foregroundLocationProvider.currentLocation(LocationPermissionState.Denied),
        )
        assertEquals(
            "com.gasstation.core.location.AndroidForegroundLocationProvider",
            foregroundLocationProvider::class.qualifiedName,
        )
    }
}
