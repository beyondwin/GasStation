package com.gasstation.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gasstation.core.designsystem.component.LegacyListRow
import com.gasstation.core.designsystem.component.LegacyTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationThemeDefaultsTest {
    @Test
    fun `dynamic color is disabled by default`() {
        assertFalse(GasStationThemeDefaults.dynamicColor)
    }

    @Test
    fun `legacy brand anchors stay yellow and black`() {
        assertEquals(Color(0xFFFFDC00), GasStationThemeDefaults.legacyYellow)
        assertEquals(Color(0xFF222222), GasStationThemeDefaults.legacyBlack)
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

@Composable
private fun legacyChromeApisCompile() {
    LegacyTopBar(
        title = {
            Text(text = "가격순")
        },
    )

    LegacyListRow(
        title = "주유소",
        subtitle = "GS칼텍스",
        meta = "120m",
        trailingContent = {
            Text(text = "1,599원")
        },
    )

    LegacyListRow(
        title = "찾기 범위",
        subtitle = "현재 설정",
        trailingContent = {
            Text(text = "5km")
        },
    )
}
