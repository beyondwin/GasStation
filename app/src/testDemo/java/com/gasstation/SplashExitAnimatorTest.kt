package com.gasstation

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = android.app.Application::class)
class SplashExitAnimatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun restoreAnimatorScale() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }

    @Test
    fun `animations disabled removes splash immediately`() {
        var removals = 0

        val result = SplashExitAnimator().animate(
            splashView = View(context),
            iconView = View(context),
            animationsEnabled = false,
            onRemove = { removals += 1 },
        )

        assertNull(result)
        assertEquals(1, removals)
    }

    @Test
    fun `enabled exit uses exact duration and end values`() {
        val splashView = View(context)
        val iconView = View(context)
        var removals = 0

        val animator = SplashExitAnimator().animate(
            splashView = splashView,
            iconView = iconView,
            animationsEnabled = true,
            onRemove = { removals += 1 },
        )

        assertNotNull(animator)
        assertEquals(180L, animator?.duration)
        animator?.end()
        assertEquals(0f, splashView.alpha)
        assertEquals(1.06f, iconView.scaleX)
        assertEquals(1.06f, iconView.scaleY)
        assertEquals(1, removals)
    }

    @Test
    fun `cancel and end remove provider only once`() {
        var removals = 0
        val animator = requireNotNull(
            SplashExitAnimator().animate(
                splashView = View(context),
                iconView = View(context),
                animationsEnabled = true,
                onRemove = { removals += 1 },
            ),
        )

        animator.cancel()
        animator.end()

        assertEquals(1, removals)
    }

    @Test
    fun `system animator scale controls reduced motion`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        assertFalse(context.areSystemAnimationsEnabled())

        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        assertTrue(context.areSystemAnimationsEnabled())
    }
}
