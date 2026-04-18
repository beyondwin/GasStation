package com.gasstation.core.designsystem

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color

object GasStationThemeDefaults {
    val dynamicColor: Boolean = false

    val legacyYellow: Color = ColorYellow
    val legacyBlack: Color = ColorBlack
    val statusBarStyle = GasStationStatusBarStyle(
        backgroundColor = legacyBlack,
        useDarkIcons = false,
    )
    val typography: GasStationTypography = DefaultGasStationTypography
    val spacing: GasStationSpacing = DefaultGasStationSpacing
    val corner: GasStationCorner = DefaultGasStationCorner
    val stroke: GasStationStroke = DefaultGasStationStroke
    val iconSize: GasStationIconSize = DefaultGasStationIconSize
    val materialTypography: Typography = DefaultMaterialTypography

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun shouldUseDynamicColor(
        dynamicColor: Boolean,
        sdkInt: Int,
    ): Boolean = dynamicColor && sdkInt >= Build.VERSION_CODES.S

    val lightColorScheme = lightColorScheme(
        primary = ColorYellow,
        onPrimary = ColorBlack,
        primaryContainer = ColorYellow,
        onPrimaryContainer = ColorBlack,
        secondary = ColorBlack,
        onSecondary = ColorYellow,
        secondaryContainer = ColorGray4,
        onSecondaryContainer = ColorBlack,
        tertiary = ColorGray2,
        onTertiary = ColorWhite,
        background = ColorYellow,
        onBackground = ColorBlack,
        surface = ColorWhite,
        onSurface = ColorBlack,
        surfaceVariant = ColorGray4,
        onSurfaceVariant = ColorGray2,
        outline = ColorGray2,
        outlineVariant = ColorGray,
        error = ColorSupportError,
        onError = ColorWhite,
        errorContainer = Color(0xFFFFEBEE),
        onErrorContainer = ColorSupportError,
        inversePrimary = ColorYellow,
        inverseSurface = ColorBlack,
        inverseOnSurface = ColorYellow,
        surfaceTint = ColorYellow,
        scrim = ColorBlack.copy(alpha = 0.32f),
    )

    val darkColorScheme = darkColorScheme(
        primary = ColorYellow,
        onPrimary = ColorBlack,
        primaryContainer = ColorBlack,
        onPrimaryContainer = ColorYellow,
        secondary = ColorWhite,
        onSecondary = ColorBlack,
        secondaryContainer = ColorGray2,
        onSecondaryContainer = ColorWhite,
        tertiary = ColorGray3,
        onTertiary = ColorBlack,
        background = ColorBlack,
        onBackground = ColorWhite,
        surface = ColorPrimaryDark,
        onSurface = ColorWhite,
        surfaceVariant = ColorBlack,
        onSurfaceVariant = ColorGray,
        outline = ColorGray3,
        outlineVariant = ColorGray2,
        error = ColorSupportError,
        onError = ColorWhite,
        errorContainer = Color(0xFF601410),
        onErrorContainer = Color(0xFFFFDAD4),
        inversePrimary = ColorBlack,
        inverseSurface = ColorWhite,
        inverseOnSurface = ColorBlack,
        surfaceTint = ColorYellow,
        scrim = ColorBlack.copy(alpha = 0.5f),
    )
}

data class GasStationStatusBarStyle(
    val backgroundColor: Color,
    val useDarkIcons: Boolean,
)
