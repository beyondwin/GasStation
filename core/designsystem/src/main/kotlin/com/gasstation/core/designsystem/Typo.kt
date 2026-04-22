package com.gasstation.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class GasStationTypography(
    val topBarTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val priceHero: TextStyle,
    val metricValue: TextStyle,
    val body: TextStyle,
    val meta: TextStyle,
    val chip: TextStyle,
    val bannerTitle: TextStyle,
    val bannerBody: TextStyle,
)

@Immutable
data class GasStationSpacing(
    val space4: Dp,
    val space8: Dp,
    val space12: Dp,
    val space16: Dp,
    val space24: Dp,
)

@Immutable
data class GasStationCorner(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
)

@Immutable
data class GasStationStroke(
    val default: Dp,
    val emphasis: Dp,
)

@Immutable
data class GasStationIconSize(
    val topBarAction: Dp,
    val trailingAction: Dp,
    val status: Dp,
)

private val DefaultFontFamily = FontFamily.Default

internal val DefaultGasStationTypography = GasStationTypography(
    topBarTitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    sectionTitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    cardTitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    priceHero = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
    metricValue = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
    body = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    meta = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    chip = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    bannerTitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bannerBody = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)

internal val DefaultGasStationSpacing = GasStationSpacing(
    space4 = 4.dp,
    space8 = 8.dp,
    space12 = 12.dp,
    space16 = 16.dp,
    space24 = 24.dp,
)

internal val DefaultGasStationCorner = GasStationCorner(
    small = 12.dp,
    medium = 18.dp,
    large = 20.dp,
)

internal val DefaultGasStationStroke = GasStationStroke(
    default = 2.dp,
    emphasis = 3.dp,
)

internal val DefaultGasStationIconSize = GasStationIconSize(
    topBarAction = 24.dp,
    trailingAction = 20.dp,
    status = 16.dp,
)

internal val DefaultMaterialTypography = Typography(
    bodyLarge = DefaultGasStationTypography.body,
    bodyMedium = DefaultGasStationTypography.bannerBody,
    titleLarge = DefaultGasStationTypography.topBarTitle,
    titleMedium = DefaultGasStationTypography.cardTitle,
    titleSmall = DefaultGasStationTypography.meta,
    labelLarge = DefaultGasStationTypography.priceHero,
    labelMedium = DefaultGasStationTypography.metricValue,
    labelSmall = DefaultGasStationTypography.chip,
)
