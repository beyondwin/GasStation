package com.gasstation.core.location

import android.content.ContextWrapper
import com.gasstation.core.model.Coordinates
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Optional
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidForegroundLocationProviderTest {
    @Test
    fun `timeout returns timed out result`() = runTest {
        val client = FakeCurrentLocationClient()
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val result = async {
            provider.currentLocation(LocationPermissionState.PreciseGranted)
        }

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(LocationLookupResult.TimedOut, result.await())
        assertTrue(client.lastCancellationTokenSource!!.token.isCancellationRequested)
    }

    @Test
    fun `unexpected provider exception returns error result`() = runTest {
        val client = FakeCurrentLocationClient(callbackFailure = IllegalStateException("gps stack crashed"))
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val result = provider.currentLocation(LocationPermissionState.PreciseGranted)

        assertTrue(result is LocationLookupResult.Error)
    }

    @Test
    fun `synchronous provider exception returns error result`() = runTest {
        val client = FakeCurrentLocationClient(syncFailure = IllegalStateException("gps stack crashed"))
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val result = provider.currentLocation(LocationPermissionState.PreciseGranted)

        assertTrue(result is LocationLookupResult.Error)
    }

    @Test
    fun `coroutine cancellation cancels location token`() = runTest {
        val client = FakeCurrentLocationClient()
        val provider = AndroidForegroundLocationProvider(
            context = ContextWrapper(null),
            demoLocationOverride = Optional.empty(),
            currentLocationClient = client,
        )

        val job = launch {
            provider.currentLocation(LocationPermissionState.PreciseGranted)
        }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(client.lastCancellationTokenSource!!.token.isCancellationRequested)
    }

    private class FakeCurrentLocationClient(
        private val coordinates: Coordinates? = null,
        private val callbackFailure: Throwable? = null,
        private val syncFailure: Exception? = null,
    ) : CurrentLocationClient {
        var lastCancellationTokenSource: CancellationTokenSource? = null

        override fun getCurrentLocation(
            context: android.content.Context,
            priority: Int,
            cancellationTokenSource: CancellationTokenSource,
            onSuccess: (Coordinates?) -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            syncFailure?.let { throw it }
            lastCancellationTokenSource = cancellationTokenSource
            when {
                callbackFailure != null -> onFailure(callbackFailure)
                coordinates != null -> onSuccess(coordinates)
            }
        }
    }
}
