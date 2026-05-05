package com.gasstation.core.location

import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class GeocoderAsyncLookupTest {
    @Test
    fun `async lookup resumes with geocoded addresses`() = runTest {
        val expected = listOf(Address(Locale.KOREA))
        val lookup = FakeGeocoderAsyncLookup { listener ->
            listener.onGeocode(expected)
        }

        val result = lookup.awaitFromLocation(
            latitude = 37.498095,
            longitude = 127.027610,
            maxResults = 1,
        )

        assertEquals(expected, result)
        assertEquals(37.498095, lookup.lastLatitude, 0.0)
        assertEquals(127.027610, lookup.lastLongitude, 0.0)
        assertEquals(1, lookup.lastMaxResults)
    }

    @Test
    fun `async lookup propagates geocoder callback error`() = runTest {
        val lookup = FakeGeocoderAsyncLookup { listener ->
            listener.onError("network unavailable")
        }

        val result = runCatching {
            lookup.awaitFromLocation(
                latitude = 37.498095,
                longitude = 127.027610,
                maxResults = 1,
            )
        }

        val exception = result.exceptionOrNull()
        assertTrue(exception is GeocoderLookupException)
        assertEquals("network unavailable", exception?.message)
    }

    @Test
    fun `async lookup keeps empty successful geocode results distinct from callback errors`() = runTest {
        val lookup = FakeGeocoderAsyncLookup { listener ->
            listener.onGeocode(emptyList())
        }

        val result = lookup.awaitFromLocation(
            latitude = 37.498095,
            longitude = 127.027610,
            maxResults = 1,
        )

        assertEquals(emptyList<Address>(), result)
    }

    @Test
    fun `async lookup propagates synchronous geocoder failures`() = runTest {
        val lookup = FakeGeocoderAsyncLookup {
            throw IOException("geocoder failed")
        }

        val result = runCatching {
            lookup.awaitFromLocation(
                latitude = 37.498095,
                longitude = 127.027610,
                maxResults = 1,
            )
        }

        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `async lookup ignores callbacks after coroutine cancellation`() = runTest {
        val lookup = FakeGeocoderAsyncLookup()
        val job = async<List<Address>?> {
            lookup.awaitFromLocation(
                latitude = 37.498095,
                longitude = 127.027610,
                maxResults = 1,
            )
        }
        runCurrent()

        job.cancelAndJoin()
        lookup.listener!!.onGeocode(listOf(Address(Locale.KOREA)))

        assertTrue(job.isCancelled)
    }
}

private class FakeGeocoderAsyncLookup(
    private val onLookup: (Geocoder.GeocodeListener) -> Unit = {},
) : GeocoderAsyncLookup {
    var lastLatitude: Double = Double.NaN
    var lastLongitude: Double = Double.NaN
    var lastMaxResults: Int = -1
    var listener: Geocoder.GeocodeListener? = null

    override fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int,
        listener: Geocoder.GeocodeListener,
    ) {
        lastLatitude = latitude
        lastLongitude = longitude
        lastMaxResults = maxResults
        this.listener = listener
        onLookup(listener)
    }
}
