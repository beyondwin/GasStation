package com.gasstation.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChromeContractsTest {
    @Test
    fun `prominent numeric emphasis is reserved for price hero and metric value`() {
        assertEquals(
            setOf(
                ChromeTextRole.PriceHero,
                ChromeTextRole.MetricValue,
            ),
            ChromeTextRole.entries
                .filter(ChromeTextRole::isProminentNumericEmphasis)
                .toSet(),
        )
    }

    @Test
    fun `text roles resolve to the approved fallback material slots`() {
        assertEquals(MaterialTypographySlot.TitleLarge, ChromeTextRole.TopBarTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.TitleMedium, ChromeTextRole.SectionTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.TitleMedium, ChromeTextRole.CardTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelLarge, ChromeTextRole.PriceHero.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelMedium, ChromeTextRole.MetricValue.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.BodyMedium, ChromeTextRole.Body.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelSmall, ChromeTextRole.Meta.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelSmall, ChromeTextRole.Chip.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.TitleSmall, ChromeTextRole.BannerTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelSmall, ChromeTextRole.BannerBody.fallbackMaterialSlot)
    }

    @Test
    fun `status banner content preserves title then body hierarchy`() {
        val content = StatusBannerContent(
            title = "위치 권한이 필요합니다",
            body = "권한을 허용하면 가까운 주유소를 불러옵니다.",
        )

        assertEquals(
            listOf(
                TextSlotRole(
                    slot = StructuredTextSlot.Title,
                    role = ChromeTextRole.BannerTitle,
                ),
                TextSlotRole(
                    slot = StructuredTextSlot.Body,
                    role = ChromeTextRole.BannerBody,
                ),
            ),
            content.orderedSlots(),
        )
    }

    @Test
    fun `status banner title is required`() {
        assertThrows(IllegalArgumentException::class.java) {
            StatusBannerContent(title = "   ")
        }
    }

    @Test
    fun `chrome card structured sections always render in approved order`() {
        val structure = ChromeCardStructure(
            hasHeader = true,
            hasPrimaryMetric = true,
            hasSupportingInfo = true,
            hasActions = true,
        )

        assertEquals(
            listOf(
                ChromeCardSection.Header,
                ChromeCardSection.PrimaryMetric,
                ChromeCardSection.SupportingInfo,
                ChromeCardSection.Actions,
            ),
            structure.orderedSections(),
        )
    }
}
