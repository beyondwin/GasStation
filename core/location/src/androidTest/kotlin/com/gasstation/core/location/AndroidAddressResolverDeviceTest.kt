package com.gasstation.core.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AndroidAddressResolverDeviceTest {
    @Test
    fun api33GeocoderCallbackPathReturnsTerminalResult() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeTrue(Geocoder.isPresent())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = AndroidAddressResolver(context)

        val result = withTimeout(GEOCODER_TIMEOUT_MILLIS) {
            resolver.addressFor(
                Coordinates(
                    latitude = 37.498095,
                    longitude = 127.027610,
                ),
            )
        }

        assertTrue(
            "Geocoder should return a terminal domain result, not hang",
            result is LocationAddressLookupResult.Success ||
                result is LocationAddressLookupResult.Unavailable ||
                result is LocationAddressLookupResult.Error,
        )
    }

    private companion object {
        const val GEOCODER_TIMEOUT_MILLIS = 10_000L
    }
}
