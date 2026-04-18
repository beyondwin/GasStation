package com.gasstation.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun GasStationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = GasStationThemeDefaults.dynamicColor,
    statusBarStyle: GasStationStatusBarStyle? = GasStationThemeDefaults.statusBarStyle,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        GasStationThemeDefaults.shouldUseDynamicColor(
            dynamicColor = dynamicColor,
            sdkInt = Build.VERSION.SDK_INT,
        ) -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GasStationThemeDefaults.darkColorScheme
        else -> GasStationThemeDefaults.lightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            statusBarStyle?.let {
                window.statusBarColor = it.backgroundColor.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    it.useDarkIcons
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
