package com.gasstation.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LocalGasStationTypography = staticCompositionLocalOf {
    GasStationThemeDefaults.typography
}
private val LocalGasStationSpacing = staticCompositionLocalOf {
    GasStationThemeDefaults.spacing
}
private val LocalGasStationCorner = staticCompositionLocalOf {
    GasStationThemeDefaults.corner
}
private val LocalGasStationStroke = staticCompositionLocalOf {
    GasStationThemeDefaults.stroke
}
private val LocalGasStationIconSize = staticCompositionLocalOf {
    GasStationThemeDefaults.iconSize
}

object GasStationTheme {
    val typography: GasStationTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalGasStationTypography.current

    val spacing: GasStationSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalGasStationSpacing.current

    val corner: GasStationCorner
        @Composable
        @ReadOnlyComposable
        get() = LocalGasStationCorner.current

    val stroke: GasStationStroke
        @Composable
        @ReadOnlyComposable
        get() = LocalGasStationStroke.current

    val iconSize: GasStationIconSize
        @Composable
        @ReadOnlyComposable
        get() = LocalGasStationIconSize.current
}

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

    CompositionLocalProvider(
        LocalGasStationTypography provides GasStationThemeDefaults.typography,
        LocalGasStationSpacing provides GasStationThemeDefaults.spacing,
        LocalGasStationCorner provides GasStationThemeDefaults.corner,
        LocalGasStationStroke provides GasStationThemeDefaults.stroke,
        LocalGasStationIconSize provides GasStationThemeDefaults.iconSize,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GasStationThemeDefaults.materialTypography,
            content = content,
        )
    }
}
