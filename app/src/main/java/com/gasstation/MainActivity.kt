package com.gasstation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.GasStationStatusBarStyle
import com.gasstation.core.designsystem.GasStationThemeDefaults
import com.gasstation.map.ExternalMapLauncher
import com.gasstation.navigation.GasStationNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var externalMapLauncher: ExternalMapLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GasStation)
        applySystemBars()
        super.onCreate(savedInstanceState)
        setContent {
            GasStationTheme {
                GasStationNavHost(
                    externalMapLauncher = externalMapLauncher,
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
