package com.gasstation.core.location

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocationAvailabilityFlowTest {
    @Test
    fun `provider change broadcast emits updated availability`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = shadowOf(locationManager)

        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        context.locationAvailabilityFlow().test {
            assertEquals(false, awaitItem())

            shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(true, awaitItem())
        }
    }
}
