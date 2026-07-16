package com.gasstation.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationSectionHeading
import com.gasstation.core.designsystem.component.GasStationTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationThemeDefaultsTest {
    @Test
    fun `dynamic color is disabled by default`() {
        assertFalse(GasStationThemeDefaults.dynamicColor)
    }

    @Test
    fun `legacy brand anchors stay yellow and black`() {
        assertEquals(Color(0xFFFFDC00), ColorYellow)
        assertEquals(Color(0xFF222222), ColorBlack)
        assertEquals(Color(0xFFFFDC00), GasStationThemeDefaults.legacyYellow)
        assertEquals(Color(0xFF222222), GasStationThemeDefaults.legacyBlack)
    }

    @Test
    fun `legacy neutral names remain aliases for tinted semantic tokens`() {
        assertEquals(ColorSurface, ColorWhite)
        assertEquals(ColorNeutralLine, ColorGray)
        assertEquals(ColorNeutralMuted, ColorGray2)
        assertEquals(ColorNeutralSubtle, ColorGray3)
        assertEquals(ColorNeutralWash, ColorGray4)
        assertEquals(ColorGray4, ColorGrayLight)

        assertNotEquals(Color(0xFFFFFFFF), ColorSurface)
        assertNotEquals(Color(0xFFF2F2F2), ColorNeutralWash)
    }

    @Test
    fun `light color scheme uses semantic tinted surfaces and neutrals`() {
        val scheme = GasStationThemeDefaults.lightColorScheme

        assertEquals(ColorSurface, scheme.surface)
        assertEquals(ColorSurfaceRaised, scheme.surfaceVariant)
        assertEquals(ColorSurfaceMuted, scheme.secondaryContainer)
        assertEquals(ColorNeutralMuted, scheme.onSurfaceVariant)
        assertEquals(ColorNeutralMuted, scheme.outline)
        assertEquals(ColorNeutralLine, scheme.outlineVariant)
        assertEquals(ColorSupportErrorContainer, scheme.errorContainer)
    }

    @Test
    fun `light theme uses ivory canvas and keeps yellow as primary signal`() {
        assertEquals(ColorSurface, GasStationThemeDefaults.lightColorScheme.background)
        assertEquals(ColorSurface, GasStationThemeDefaults.lightColorScheme.surface)
        assertEquals(ColorYellow, GasStationThemeDefaults.lightColorScheme.primary)
    }

    @Test
    fun `dark color scheme keeps brand anchors with tinted inverse surfaces`() {
        val scheme = GasStationThemeDefaults.darkColorScheme

        assertEquals(ColorYellow, scheme.primary)
        assertEquals(ColorBlack, scheme.background)
        assertEquals(ColorSurfaceInverse, scheme.surface)
        assertEquals(ColorSurfaceInverseVariant, scheme.surfaceVariant)
        assertEquals(ColorSurface, scheme.onSurface)
        assertEquals(ColorNeutralLine, scheme.onSurfaceVariant)
        assertTrue(contrastRatio(ColorSupportInfoOnDark, scheme.background) >= 4.5f)
        assertTrue(contrastRatio(scheme.onErrorContainer, scheme.background) >= 4.5f)
    }

    @Test
    fun `dynamic color can be explicitly enabled on supported sdk`() {
        assertTrue(
            GasStationThemeDefaults.shouldUseDynamicColor(
                dynamicColor = true,
                sdkInt = 31,
            ),
        )
        assertFalse(
            GasStationThemeDefaults.shouldUseDynamicColor(
                dynamicColor = true,
                sdkInt = 30,
            ),
        )
        assertFalse(
            GasStationThemeDefaults.shouldUseDynamicColor(
                dynamicColor = false,
                sdkInt = 31,
            ),
        )
    }

    @Test
    fun `default status bar style keeps legacy black bar with light icons`() {
        assertEquals(
            GasStationThemeDefaults.legacyBlack,
            GasStationThemeDefaults.statusBarStyle.backgroundColor,
        )
        assertFalse(GasStationThemeDefaults.statusBarStyle.useDarkIcons)
    }
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

@Composable
private fun ChromeApisCompile() {
    GasStationTopBar(
        title = {
            Text(text = "가격순")
        },
    )

    GasStationCard {
        GasStationSectionHeading(
            title = "현재 조건",
            subtitle = "가까운 순으로 정렬합니다.",
        )
    }
}
