package com.gasstation

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AppIconResourceTest {
    @Test
    fun `launcher icon resolves to an adaptive icon on android 8 and above`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val drawable = context.getDrawable(R.mipmap.ic_launcher)

        assertTrue(
            "ic_launcher should resolve to AdaptiveIconDrawable on API 26+",
            drawable is AdaptiveIconDrawable,
        )
    }

    @Test
    @Config(sdk = [24])
    fun `launcher icon still resolves on pre-adaptive-icon android versions`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertNotNull(context.getDrawable(R.mipmap.ic_launcher))
    }
}
