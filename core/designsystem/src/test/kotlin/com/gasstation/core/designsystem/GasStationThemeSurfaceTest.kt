package com.gasstation.core.designsystem

import java.lang.reflect.Modifier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationThemeSurfaceTest {
    @Test
    fun `theme entry point lives in core designsystem package`() {
        val facadeClass = Class.forName("com.gasstation.core.designsystem.ThemeKt")
        val gasStationTheme = facadeClass.declaredMethods.singleOrNull { method ->
            method.name == "GasStationTheme"
        }

        assertNotNull(
            "Expected a top-level GasStationTheme entry point in com.gasstation.core.designsystem.",
            gasStationTheme,
        )
        assertTrue(
            "Expected GasStationTheme to be publicly accessible from the designsystem facade.",
            Modifier.isPublic(gasStationTheme!!.modifiers),
        )
    }

    @Test
    fun `theme facade exposes app token accessors`() {
        val themeClass = Class.forName("com.gasstation.core.designsystem.GasStationTheme")
        val methodNames = themeClass.declaredMethods.map { method -> method.name }.toSet()

        assertTrue(
            "Expected GasStationTheme to expose a typography accessor for app-owned text roles.",
            methodNames.contains("getTypography"),
        )
        assertTrue(
            "Expected GasStationTheme to expose spacing tokens.",
            methodNames.contains("getSpacing"),
        )
        assertTrue(
            "Expected GasStationTheme to expose corner tokens.",
            methodNames.contains("getCorner"),
        )
        assertTrue(
            "Expected GasStationTheme to expose stroke tokens.",
            methodNames.contains("getStroke"),
        )
        assertTrue(
            "Expected GasStationTheme to expose icon size tokens.",
            methodNames.contains("getIconSize"),
        )
    }
}
