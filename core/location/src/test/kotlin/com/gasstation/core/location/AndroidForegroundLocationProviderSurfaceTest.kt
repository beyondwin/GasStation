package com.gasstation.core.location

import android.content.ContextWrapper
import com.gasstation.core.model.Coordinates
import java.util.Optional
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidForegroundLocationProviderSurfaceTest {
    @Test
    fun `android foreground provider is owned by core location`() {
        assertEquals(
            "com.gasstation.core.location.AndroidForegroundLocationProvider",
            AndroidForegroundLocationProvider::class.qualifiedName,
        )
    }

    @Test
    fun `demo override wins before denied permission result`() = runBlocking {
        val expected = Coordinates(latitude = 37.498095, longitude = 127.02761)
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.of(DemoLocationOverride { expected }),
            currentLocationClient = unusedCurrentLocationClient(),
        )

        assertEquals(
            LocationLookupResult.Success(expected),
            provider.currentLocation(LocationPermissionState.Denied),
        )
    }

    @Test
    fun `demo override returning null short circuits fallback`() = runBlocking {
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.of(DemoLocationOverride { null }),
            currentLocationClient = unusedCurrentLocationClient(),
        )

        assertEquals(
            LocationLookupResult.Unavailable,
            provider.currentLocation(LocationPermissionState.PreciseGranted),
        )
    }

    @Test
    fun `denied permission without override returns permission denied`() = runBlocking {
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty<DemoLocationOverride>(),
            currentLocationClient = unusedCurrentLocationClient(),
        )

        assertEquals(
            LocationLookupResult.PermissionDenied,
            provider.currentLocation(LocationPermissionState.Denied),
        )
    }

    private fun unusedCurrentLocationClient(): CurrentLocationClient = CurrentLocationClient {
            _,
            _,
            _,
            _,
            _,
        ->
        throw AssertionError("CurrentLocationClient should not be used in this test")
    }
}
