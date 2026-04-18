package com.gasstation.map

import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.gasstation.domain.station.model.MapProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ExternalMapLauncherTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `kakao map route intent includes origin and destination coordinates`() {
        shadowOf(application.packageManager).installPackage(
            PackageInfo().apply {
                packageName = "net.daum.android.map"
            },
        )
        val launcher = IntentExternalMapLauncher(application)

        launcher.open(
            provider = MapProvider.KAKAO_NAVI,
            stationName = "강남주유소",
            originLatitude = 37.498095,
            originLongitude = 127.027610,
            latitude = 37.499095,
            longitude = 127.128610,
        )

        val startedIntent = shadowOf(application).nextStartedActivity

        assertEquals("android.intent.action.VIEW", startedIntent.action)
        assertTrue(
            startedIntent.dataString!!.contains("sp=37.498095,127.02761"),
        )
        assertTrue(
            startedIntent.dataString!!.contains("ep=37.499095,127.12861"),
        )
        assertTrue(
            startedIntent.dataString!!.contains("ename=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C"),
        )
    }
}
