package com.gasstation.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LegacyChromeContractsTest {
    @Test
    fun `prominent numeric emphasis is reserved for price hero and metric value`() {
        assertEquals(
            setOf(
                LegacyChromeTextRole.PriceHero,
                LegacyChromeTextRole.MetricValue,
            ),
            LegacyChromeTextRole.entries
                .filter(LegacyChromeTextRole::isProminentNumericEmphasis)
                .toSet(),
        )
    }

    @Test
    fun `text roles resolve to the approved fallback material slots`() {
        assertEquals(LegacyMaterialTypographySlot.TitleLarge, LegacyChromeTextRole.TopBarTitle.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.TitleMedium, LegacyChromeTextRole.SectionTitle.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.TitleMedium, LegacyChromeTextRole.CardTitle.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.LabelLarge, LegacyChromeTextRole.PriceHero.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.LabelMedium, LegacyChromeTextRole.MetricValue.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.BodyMedium, LegacyChromeTextRole.Body.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.LabelSmall, LegacyChromeTextRole.Meta.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.LabelSmall, LegacyChromeTextRole.Chip.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.TitleSmall, LegacyChromeTextRole.BannerTitle.fallbackMaterialSlot)
        assertEquals(LegacyMaterialTypographySlot.LabelSmall, LegacyChromeTextRole.BannerBody.fallbackMaterialSlot)
    }

    @Test
    fun `status banner content preserves title then body hierarchy`() {
        val content = LegacyStatusBannerContent(
            title = "위치 권한이 필요합니다",
            body = "권한을 허용하면 가까운 주유소를 불러옵니다.",
        )

        assertEquals(
            listOf(
                LegacyTextSlotRole(
                    slot = LegacyStructuredTextSlot.Title,
                    role = LegacyChromeTextRole.BannerTitle,
                ),
                LegacyTextSlotRole(
                    slot = LegacyStructuredTextSlot.Body,
                    role = LegacyChromeTextRole.BannerBody,
                ),
            ),
            content.orderedSlots(),
        )
    }

    @Test
    fun `status banner title is required`() {
        assertThrows(IllegalArgumentException::class.java) {
            LegacyStatusBannerContent(title = "   ")
        }
    }

    @Test
    fun `list row content preserves overline title subtitle meta hierarchy`() {
        val content = LegacyListRowContent(
            overline = "현재 설정",
            title = "찾기 범위",
            subtitle = "가까운 순으로 정렬합니다.",
            meta = "5km",
        )

        assertEquals(
            listOf(
                LegacyTextSlotRole(
                    slot = LegacyStructuredTextSlot.Overline,
                    role = LegacyChromeTextRole.Meta,
                ),
                LegacyTextSlotRole(
                    slot = LegacyStructuredTextSlot.Title,
                    role = LegacyChromeTextRole.CardTitle,
                ),
                LegacyTextSlotRole(
                    slot = LegacyStructuredTextSlot.Subtitle,
                    role = LegacyChromeTextRole.Body,
                ),
                LegacyTextSlotRole(
                    slot = LegacyStructuredTextSlot.Meta,
                    role = LegacyChromeTextRole.Meta,
                ),
            ),
            content.orderedSlots(),
        )
    }

    @Test
    fun `list row title is required`() {
        assertThrows(IllegalArgumentException::class.java) {
            LegacyListRowContent(title = "")
        }
    }

    @Test
    fun `chrome card structured sections always render in approved order`() {
        val structure = LegacyChromeCardStructure(
            hasHeader = true,
            hasPrimaryMetric = true,
            hasSupportingInfo = true,
            hasActions = true,
        )

        assertEquals(
            listOf(
                LegacyChromeCardSection.Header,
                LegacyChromeCardSection.PrimaryMetric,
                LegacyChromeCardSection.SupportingInfo,
                LegacyChromeCardSection.Actions,
            ),
            structure.orderedSections(),
        )
    }
}
