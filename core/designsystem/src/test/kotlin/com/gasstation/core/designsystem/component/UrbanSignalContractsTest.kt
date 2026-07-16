package com.gasstation.core.designsystem.component

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gasstation.core.designsystem.GasStationThemeDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class UrbanSignalContractsTest {
    @Test
    fun `urban signal density tokens match approved contract`() {
        assertEquals(50.dp, UrbanSignalTokens.mainLogoTileSize)
        assertEquals(38.dp, UrbanSignalTokens.mainLogoSize)
        assertEquals(120.dp, UrbanSignalTokens.mainRowMinHeight)
        assertEquals(44.dp, UrbanSignalTokens.compactLogoTileSize)
        assertEquals(34.dp, UrbanSignalTokens.compactLogoSize)
        assertEquals(108.dp, UrbanSignalTokens.compactRowMinHeight)
        assertEquals(48.dp, UrbanSignalTokens.minimumTouchTarget)
        assertEquals(28.sp, GasStationThemeDefaults.typography.compactPriceHero.fontSize)
        assertEquals("tnum", GasStationThemeDefaults.typography.compactPriceHero.fontFeatureSettings)
    }
}
