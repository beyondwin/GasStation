package com.gasstation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen

private const val SPLASH_EXIT_DURATION_MILLIS = 180L
private const val SPLASH_EXIT_ICON_SCALE = 1.06f

internal class SplashExitAnimator(private val animationsEnabled: (Context) -> Boolean = Context::areSystemAnimationsEnabled) {
    fun install(splashScreen: SplashScreen, context: Context) {
        splashScreen.setOnExitAnimationListener { provider ->
            animate(
                splashView = provider.view,
                iconView = provider.iconView,
                animationsEnabled = animationsEnabled(context),
                onRemove = provider::remove,
            )
        }
    }

    internal fun animate(splashView: View, iconView: View, animationsEnabled: Boolean, onRemove: () -> Unit): AnimatorSet? {
        val cleanup = OneShotSplashRemoval(onRemove)
        if (!animationsEnabled) {
            cleanup.removeNow()
            return null
        }

        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, SPLASH_EXIT_ICON_SCALE),
                ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, SPLASH_EXIT_ICON_SCALE),
            )
            duration = SPLASH_EXIT_DURATION_MILLIS
            interpolator = DecelerateInterpolator()
            addListener(cleanup)
            start()
        }
    }
}

internal fun Context.areSystemAnimationsEnabled(): Boolean = Settings.Global.getFloat(
    contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f,
) > 0f

private class OneShotSplashRemoval(private val onRemove: () -> Unit) : AnimatorListenerAdapter() {
    private var removed = false

    override fun onAnimationEnd(animation: Animator) {
        removeNow()
    }

    override fun onAnimationCancel(animation: Animator) {
        removeNow()
    }

    fun removeNow() {
        if (removed) return
        removed = true
        onRemove()
    }
}
