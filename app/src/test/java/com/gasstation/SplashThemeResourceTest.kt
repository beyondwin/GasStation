package com.gasstation

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SplashThemeResourceTest {
    @Test
    fun `main activity theme exposes branded splash background and icon on android 12 and above`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        val themedContext = ContextThemeWrapper(context, activityInfo.themeResource)
        val splashBackground = TypedValue()
        val splashIcon = TypedValue()

        assertTrue(
            "MainActivity theme should define windowSplashScreenBackground",
            themedContext.theme.resolveAttribute(
                android.R.attr.windowSplashScreenBackground,
                splashBackground,
                true,
            ),
        )
        assertEquals(R.color.ic_launcher_background, splashBackground.resourceId)

        assertTrue(
            "MainActivity theme should define windowSplashScreenAnimatedIcon",
            themedContext.theme.resolveAttribute(
                android.R.attr.windowSplashScreenAnimatedIcon,
                splashIcon,
                true,
            ),
        )
        assertEquals(R.drawable.ic_splash_foreground, splashIcon.resourceId)
    }

    @Test
    fun `fallback splash background uses an inset drawable for the centered icon`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val drawable = context.getDrawable(R.drawable.splash_screen_background)

        assertNotNull(drawable)
        assertTrue(drawable is LayerDrawable)
        val layerDrawable = drawable as LayerDrawable
        assertEquals(2, layerDrawable.numberOfLayers)
        assertTrue(
            "Splash icon layer should preserve extra breathing room to avoid visual clipping",
            layerDrawable.getDrawable(1) is InsetDrawable,
        )
    }
}
