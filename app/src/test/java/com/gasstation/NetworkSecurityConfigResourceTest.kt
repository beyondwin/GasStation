package com.gasstation

import android.app.Application
import android.security.NetworkSecurityPolicy
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NetworkSecurityConfigResourceTest {
    @Test
    fun `application uses scoped network security config instead of app wide cleartext`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
            .readText()

        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(!manifest.contains("android:usesCleartextTraffic=\"true\""))
    }

    @Test
    fun `network security config permits cleartext only for opinet host`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val xml = application.resources.getXml(R.xml.network_security_config)
        val domains = mutableListOf<Pair<String, Boolean>>()
        var cleartextDomainConfigCount = 0
        var cleartextBaseConfigCount = 0

        var event = xml.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (xml.name) {
                    "base-config" -> {
                        if (xml.getAttributeBooleanValue(null, "cleartextTrafficPermitted", false)) {
                            cleartextBaseConfigCount += 1
                        }
                    }
                    "domain-config" -> {
                        if (xml.getAttributeBooleanValue(null, "cleartextTrafficPermitted", false)) {
                            cleartextDomainConfigCount += 1
                        }
                    }
                    "domain" -> {
                        val includeSubdomains = xml.getAttributeBooleanValue(null, "includeSubdomains", false)
                        domains += xml.nextText() to includeSubdomains
                    }
                }
            }
            event = xml.next()
        }

        assertEquals(0, cleartextBaseConfigCount)
        assertEquals(1, cleartextDomainConfigCount)
        assertEquals(listOf("www.opinet.co.kr" to false), domains)
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("www.opinet.co.kr"))
        val configText = projectFile(
            "app/src/main/res/xml/network_security_config.xml",
            "src/main/res/xml/network_security_config.xml",
        ).readText()
        assertTrue(configText.contains("cleartextTrafficPermitted=\"true\""))
        assertFalse(configText.contains("includeSubdomains=\"true\""))
    }

    private fun projectFile(vararg candidates: String): File =
        candidates.map(::File).firstOrNull(File::exists)
            ?: error("Could not find any project file candidate: ${candidates.joinToString()}")
}
