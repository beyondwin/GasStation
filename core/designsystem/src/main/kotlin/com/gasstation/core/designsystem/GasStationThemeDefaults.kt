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
        secondaryContainer = ColorSurfaceMuted,
        onSecondaryContainer = ColorBlack,
        tertiary = ColorNeutralMuted,
        onTertiary = ColorSurface,
        background = ColorYellow,
        onBackground = ColorBlack,
        surface = ColorSurface,
        onSurface = ColorBlack,
        surfaceVariant = ColorSurfaceRaised,
        onSurfaceVariant = ColorNeutralMuted,
        outline = ColorNeutralMuted,
        outlineVariant = ColorNeutralLine,
        error = ColorSupportError,
        onError = ColorSurface,
        errorContainer = ColorSupportErrorContainer,
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
        secondary = ColorSurface,
        onSecondary = ColorBlack,
        secondaryContainer = ColorSurfaceInverseVariant,
        onSecondaryContainer = ColorSurface,
        tertiary = ColorNeutralLine,
        onTertiary = ColorBlack,
        background = ColorBlack,
        onBackground = ColorSurface,
        surface = ColorSurfaceInverse,
        onSurface = ColorSurface,
        surfaceVariant = ColorSurfaceInverseVariant,
        onSurfaceVariant = ColorNeutralLine,
        outline = ColorNeutralSubtle,
        outlineVariant = ColorNeutralMuted,
        error = ColorSupportError,
        onError = ColorSurface,
        errorContainer = Color(0xFF601410),
        onErrorContainer = Color(0xFFFFDAD4),
        inversePrimary = ColorBlack,
        inverseSurface = ColorSurface,
        inverseOnSurface = ColorBlack,
        surfaceTint = ColorYellow,
        scrim = ColorBlack.copy(alpha = 0.5f),
    )
}

data class GasStationStatusBarStyle(
    val backgroundColor: Color,
    val useDarkIcons: Boolean,
)
