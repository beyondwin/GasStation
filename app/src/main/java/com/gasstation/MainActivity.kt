package com.gasstation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gasstation.core.designsystem.GasStationStatusBarStyle
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.GasStationThemeDefaults
import com.gasstation.map.ExternalMapLauncher
import com.gasstation.navigation.GasStationNavHost
import com.gasstation.startup.StartupDrawReporter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var externalMapLauncher: ExternalMapLauncher

    private val startupDrawReporter = StartupDrawReporter(::reportFullyDrawn)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        applySystemBars()
        super.onCreate(savedInstanceState)
        SplashExitAnimator().install(splashScreen, this)
        setContent {
            GasStationTheme {
                GasStationNavHost(
                    externalMapLauncher = externalMapLauncher,
                    onStationListFirstContentDrawn = startupDrawReporter::reportFirstContentDrawn,
                )
            }
        }
    }

    private fun applySystemBars() {
        val statusBarStyle = GasStationThemeDefaults.statusBarStyle
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle.toSystemBarStyle(),
            navigationBarStyle = statusBarStyle.toSystemBarStyle(),
        )
    }

    private fun GasStationStatusBarStyle.toSystemBarStyle(): SystemBarStyle {
        val backgroundColor = backgroundColor.toArgb()
        return if (useDarkIcons) {
            SystemBarStyle.light(
                scrim = backgroundColor,
                darkScrim = backgroundColor,
            )
        } else {
            SystemBarStyle.dark(backgroundColor)
        }
    }
}
