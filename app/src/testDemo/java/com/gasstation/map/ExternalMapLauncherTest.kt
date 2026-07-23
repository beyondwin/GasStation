package com.gasstation.map

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.model.MapProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExternalMapLauncherTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `installed providers open exact explicit browsable route targets`() {
        providerCases.forEach { case ->
            install(case.packageName)

            val result = IntentExternalMapLauncher(application).open(
                provider = case.provider,
                stationName = "강남주유소",
                originLatitude = 37.498095,
                originLongitude = 127.027610,
                latitude = 37.499095,
                longitude = 127.128610,
            )

            val startedIntent = shadowOf(application).nextStartedActivity
            assertEquals(ExternalMapLaunchResult.Opened, result)
            assertEquals(Intent.ACTION_VIEW, startedIntent.action)
            assertEquals(case.packageName, startedIntent.`package`)
            assertEquals(case.expectedUri, startedIntent.dataString)
            assertTrue(startedIntent.categories.contains(Intent.CATEGORY_BROWSABLE))
            assertHasNewTaskFlag(startedIntent)
        }
    }

    @Test
    fun `missing provider opens its market page`() {
        val result = IntentExternalMapLauncher(application).open(
            provider = MapProvider.NAVER_MAP,
            stationName = "강남주유소",
            originLatitude = 37.498095,
            originLongitude = 127.027610,
            latitude = 37.499095,
            longitude = 127.128610,
        )

        val startedIntent = shadowOf(application).nextStartedActivity
        assertEquals(ExternalMapLaunchResult.StoreOpened, result)
        assertStoreIntent(
            intent = startedIntent,
            expectedUri = "market://details?id=com.nhn.android.nmap",
        )
    }

    @Test
    fun `route and market activity-not-found failures fall back to HTTPS store`() {
        install("com.nhn.android.nmap")
        val context = RecordingFailureContext(
            base = application,
            failureFor = { intent ->
                if (intent.data?.scheme != "https") ActivityNotFoundException() else null
            },
        )

        val result = IntentExternalMapLauncher(context).open(
            provider = MapProvider.NAVER_MAP,
            stationName = "강남주유소",
            originLatitude = 37.498095,
            originLongitude = 127.027610,
            latitude = 37.499095,
            longitude = 127.128610,
        )

        assertEquals(ExternalMapLaunchResult.StoreOpened, result)
        val routeIntent = context.startedIntents[0]
        assertEquals(Intent.ACTION_VIEW, routeIntent.action)
        assertEquals("com.nhn.android.nmap", routeIntent.`package`)
        assertEquals(
            "nmap://route/car?dlat=37.499095&dlng=127.12861&dname=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&appname=com.gasstation.demo",
            routeIntent.dataString,
        )
        assertTrue(routeIntent.categories.contains(Intent.CATEGORY_BROWSABLE))
        assertHasNewTaskFlag(routeIntent)
        assertStoreIntent(
            intent = context.startedIntents[1],
            expectedUri = "market://details?id=com.nhn.android.nmap",
        )
        assertStoreIntent(
            intent = context.startedIntents[2],
            expectedUri = "https://play.google.com/store/apps/details?id=com.nhn.android.nmap",
        )
    }

    @Test
    fun `activity-not-found and security failures return failed after every fallback`() {
        install("com.nhn.android.nmap")
        val context = RecordingFailureContext(
            base = application,
            failureFor = { intent ->
                when (intent.data?.scheme) {
                    "https" -> SecurityException()
                    else -> ActivityNotFoundException()
                }
            },
        )

        val result = IntentExternalMapLauncher(context).open(
            provider = MapProvider.NAVER_MAP,
            stationName = "강남주유소",
            originLatitude = 37.498095,
            originLongitude = 127.027610,
            latitude = 37.499095,
            longitude = 127.128610,
        )

        assertEquals(ExternalMapLaunchResult.Failed, result)
        assertEquals(listOf("nmap", "market", "https"), context.startedIntents.map { it.data?.scheme })
    }

    private fun install(packageName: String) {
        shadowOf(application.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
            },
        )
    }

    private fun assertStoreIntent(intent: Intent, expectedUri: String) {
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(expectedUri, intent.dataString)
        assertEquals(null, intent.`package`)
        assertEquals(null, intent.categories)
        assertHasNewTaskFlag(intent)
    }

    private fun assertHasNewTaskFlag(intent: Intent) {
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}

private data class ProviderCase(val provider: MapProvider, val packageName: String, val expectedUri: String)

private val providerCases = listOf(
    ProviderCase(
        MapProvider.TMAP,
        "com.skt.tmap.ku",
        "tmap://route?goalx=127.12861&goaly=37.499095&goalname=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&reqCoordType=KTM&resCoordType=WGS84",
    ),
    ProviderCase(
        MapProvider.KAKAO_MAP,
        "net.daum.android.map",
        "kakaomap://route?sp=37.498095,127.02761&ep=37.499095,127.12861&ename=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&by=car",
    ),
    ProviderCase(
        MapProvider.NAVER_MAP,
        "com.nhn.android.nmap",
        "nmap://route/car?dlat=37.499095&dlng=127.12861&dname=%EA%B0%95%EB%82%A8%EC%A3%BC%EC%9C%A0%EC%86%8C&appname=com.gasstation.demo",
    ),
)

private class RecordingFailureContext(base: Context, private val failureFor: (Intent) -> RuntimeException?) : ContextWrapper(base) {
    val startedIntents = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
        startedIntents += intent
        failureFor(intent)?.let { throw it }
    }
}
