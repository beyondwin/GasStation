package com.gasstation.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationThemeTokensTest {
    @Test
    fun `theme defaults expose approved typography roles`() {
        val typography = getDefaultToken("Typography")

        expectedRoleGetterNames.forEach { roleGetterName ->
            assertTrue(
                "Expected GasStationThemeDefaults.typography to expose $roleGetterName.",
                typography.javaClass.methods.any { method -> method.name == roleGetterName },
            )
        }
    }

    @Test
    fun `large number typography stays isolated to price hero and metric value`() {
        val typography = getDefaultToken("Typography")
        val priceHero = typography.textStyle("getPriceHero")
        val metricValue = typography.textStyle("getMetricValue")

        assertEquals("tnum", priceHero.fontFeatureSettings)
        assertEquals("tnum", metricValue.fontFeatureSettings)
        assertTrue(
            "Expected PriceHero to remain the strongest number style in the system.",
            priceHero.fontSize > metricValue.fontSize,
        )

        expectedRoleGetterNames
            .filterNot { getterName -> getterName in largeNumberRoleGetterNames }
            .forEach { getterName ->
                val style = typography.textStyle(getterName)

                assertTrue(
                    "Expected $getterName to avoid tabular-number hero styling.",
                    style.fontFeatureSettings != "tnum",
                )
                assertTrue(
                    "Expected $getterName to stay below the MetricValue scale.",
                    style.fontSize < metricValue.fontSize,
                )
            }
    }

    @Test
    fun `typography roles keep neutral tracking and system font`() {
        val typography = getDefaultToken("Typography")

        expectedRoleGetterNames.forEach { getterName ->
            val style = typography.textStyle(getterName)

            assertEquals(
                "Expected $getterName to avoid optical tracking overrides.",
                0.sp,
                style.letterSpacing,
            )
            assertEquals(
                "Expected $getterName to keep Android system font for Korean readability.",
                FontFamily.Default,
                style.fontFamily,
            )
        }
    }

    @Test
    fun `typography hierarchy keeps price and metric above supporting copy`() {
        val typography = getDefaultToken("Typography")
        val priceHero = typography.textStyle("getPriceHero")
        val metricValue = typography.textStyle("getMetricValue")
        val cardTitle = typography.textStyle("getCardTitle")

        assertTrue(priceHero.fontSize > metricValue.fontSize)
        assertTrue(metricValue.fontSize > cardTitle.fontSize)

        supportingRoleGetterNames.forEach { getterName ->
            val style = typography.textStyle(getterName)

            assertTrue(
                "Expected $getterName to stay below CardTitle in the scan hierarchy.",
                cardTitle.fontSize > style.fontSize,
            )
        }
    }

    @Test
    fun `supporting copy roles do not inherit numeric display styling`() {
        val typography = getDefaultToken("Typography")

        supportingRoleGetterNames
            .plus(setOf("getSectionTitle", "getTopBarTitle"))
            .forEach { getterName ->
                val style = typography.textStyle(getterName)

                assertEquals(
                    "Expected $getterName to avoid numeric font feature settings.",
                    null,
                    style.fontFeatureSettings,
                )
            }
    }

    @Test
    fun `theme defaults expose approved spacing corner stroke and icon tokens`() {
        val spacing = getDefaultToken("Spacing")
        assertEquals(4.dp.value, spacing.dpValue("getSpace4"), 0f)
        assertEquals(8.dp.value, spacing.dpValue("getSpace8"), 0f)
        assertEquals(12.dp.value, spacing.dpValue("getSpace12"), 0f)
        assertEquals(16.dp.value, spacing.dpValue("getSpace16"), 0f)
        assertEquals(24.dp.value, spacing.dpValue("getSpace24"), 0f)

        val corner = getDefaultToken("Corner")
        assertEquals(12.dp.value, corner.dpValue("getSmall"), 0f)
        assertEquals(18.dp.value, corner.dpValue("getMedium"), 0f)
        assertEquals(20.dp.value, corner.dpValue("getLarge"), 0f)

        val stroke = getDefaultToken("Stroke")
        assertEquals(2.dp.value, stroke.dpValue("getDefault"), 0f)
        assertEquals(3.dp.value, stroke.dpValue("getEmphasis"), 0f)

        val iconSize = getDefaultToken("IconSize")
        assertEquals(24.dp.value, iconSize.dpValue("getTopBarAction"), 0f)
        assertEquals(20.dp.value, iconSize.dpValue("getTrailingAction"), 0f)
        assertEquals(16.dp.value, iconSize.dpValue("getStatus"), 0f)
    }

    @Test
    fun `theme defaults map role typography back to material fallback slots`() {
        val materialTypography = getDefaultToken("MaterialTypography")
        val titleLarge = materialTypography.textStyle("getTitleLarge")
        val titleMedium = materialTypography.textStyle("getTitleMedium")
        val labelLarge = materialTypography.textStyle("getLabelLarge")
        val labelMedium = materialTypography.textStyle("getLabelMedium")

        assertEquals(22.sp, titleLarge.fontSize)
        assertEquals(17.sp, titleMedium.fontSize)
        assertEquals(32.sp, labelLarge.fontSize)
        assertEquals("tnum", labelLarge.fontFeatureSettings)
        assertEquals(24.sp, labelMedium.fontSize)
        assertEquals("tnum", labelMedium.fontFeatureSettings)
    }

    private fun getDefaultToken(suffix: String): Any {
        val defaultsClass = GasStationThemeDefaults::class.java
        val getter = defaultsClass.methods.single { method -> method.name == "get$suffix" }
        return requireNotNull(getter.invoke(GasStationThemeDefaults)) {
            "Expected GasStationThemeDefaults.$suffix to be initialized."
        }
    }

    private fun Any.textStyle(getterName: String): TextStyle {
        val getter = javaClass.methods.single { method -> method.name == getterName }
        return getter.invoke(this) as TextStyle
    }

    private fun Any.dpValue(getterName: String): Float =
        javaClass.methods
            .single { method -> method.name == getterName || method.name.startsWith("$getterName-") }
            .invoke(this) as Float

    companion object {
        private val expectedRoleGetterNames = setOf(
            "getTopBarTitle",
            "getSectionTitle",
            "getCardTitle",
            "getPriceHero",
            "getMetricValue",
            "getBody",
            "getMeta",
            "getChip",
            "getBannerTitle",
            "getBannerBody",
        )

        private val largeNumberRoleGetterNames = setOf(
            "getPriceHero",
            "getMetricValue",
        )

        private val supportingRoleGetterNames = setOf(
            "getBody",
            "getMeta",
            "getChip",
            "getBannerTitle",
            "getBannerBody",
        )
    }
}
