package com.gasstation

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = android.app.Application::class)
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

    @Test
    fun `launcher foreground is not backed by a low resolution bitmap`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val drawable = context.getDrawable(R.drawable.ic_launcher_foreground)

        assertNotNull(drawable)
        assertFalse(
            "ic_launcher_foreground should be vector or xml based to avoid bitmap scaling artifacts",
            drawable is BitmapDrawable,
        )
    }
}
