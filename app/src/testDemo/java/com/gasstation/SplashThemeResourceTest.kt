package com.gasstation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
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
import androidx.core.splashscreen.R as SplashScreenR

@RunWith(RobolectricTestRunner::class)
class SplashThemeResourceTest {
    @Test
    @Config(sdk = [31], application = android.app.Application::class)
    fun `android 12 splash foreground is an animated vector drawable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(
            context.getDrawable(R.drawable.ic_splash_foreground) is AnimatedVectorDrawable,
        )
    }

    @Test
    @Config(sdk = [30], application = android.app.Application::class)
    fun `pre android 12 launcher resolves branded compat splash and post theme`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(
        sdk = [30],
        qualifiers = "night",
        application = android.app.Application::class,
    )
    fun `pre android 12 night mode keeps brand constant splash colors`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(sdk = [31], application = android.app.Application::class)
    fun `android 12 launcher resolves framework splash and post theme`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(
        sdk = [31],
        qualifiers = "night",
        application = android.app.Application::class,
    )
    fun `android 12 night mode keeps brand constant splash resources`() {
        val themedContext = launcherThemedContext()

        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenBackground,
            R.color.ic_launcher_background,
        )
        assertThemeResource(
            themedContext,
            android.R.attr.windowSplashScreenAnimatedIcon,
            R.drawable.ic_splash_foreground,
        )
        assertThemeResource(
            themedContext,
            SplashScreenR.attr.postSplashScreenTheme,
            R.style.Theme_GasStation,
        )
    }

    @Test
    @Config(sdk = [30], application = android.app.Application::class)
    fun `fallback splash background uses an inset drawable for the centered icon`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = context.getDrawable(R.drawable.splash_screen_background)

        assertNotNull(drawable)
        assertTrue(drawable is LayerDrawable)
        val layerDrawable = drawable as LayerDrawable
        assertEquals(2, layerDrawable.numberOfLayers)
        assertTrue(layerDrawable.getDrawable(1) is InsetDrawable)
    }

    private fun launcherThemedContext(): ContextThemeWrapper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )
        }
        return ContextThemeWrapper(context, activityInfo.themeResource)
    }

    private fun assertThemeResource(themedContext: ContextThemeWrapper, attribute: Int, expectedResource: Int) {
        val value = TypedValue()
        assertTrue(themedContext.theme.resolveAttribute(attribute, value, true))
        assertEquals(expectedResource, value.resourceId)
    }
}
