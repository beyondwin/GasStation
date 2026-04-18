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
}
