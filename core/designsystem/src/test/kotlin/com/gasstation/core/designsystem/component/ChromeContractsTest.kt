package com.gasstation.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorBlack
import com.gasstation.core.designsystem.ColorSupportError
import com.gasstation.core.designsystem.ColorSupportInfo
import com.gasstation.core.designsystem.ColorSupportSuccess
import com.gasstation.core.designsystem.ColorWhite
import com.gasstation.core.designsystem.ColorYellow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun `metric emphasis maps to approved numeric text roles`() {
        assertEquals(
            ChromeTextRole.PriceHero,
            GasStationMetricEmphasis.Primary.numberRole,
        )
        assertEquals(
            ChromeTextRole.MetricValue,
            GasStationMetricEmphasis.Secondary.numberRole,
        )
    }

    @Test
    fun `metric unit padding keeps primary numbers optically dominant`() {
        assertEquals(4.dp, GasStationMetricEmphasis.Primary.unitBottomPadding)
        assertEquals(3.dp, GasStationMetricEmphasis.Secondary.unitBottomPadding)
        assertTrue(
            "Primary metric unit should sit slightly lower than secondary unit.",
            GasStationMetricEmphasis.Primary.unitBottomPadding >
                GasStationMetricEmphasis.Secondary.unitBottomPadding,
        )
    }

    @Test
    fun `supporting info content preserves label value and trailing hierarchy`() {
        val content = SupportingInfoContent(
            label = "변동",
            value = "1,689원",
            hasTrailingContent = true,
        )

        assertEquals(
            listOf(
                SupportingInfoSlotRole(
                    slot = SupportingInfoSlot.Label,
                    role = ChromeTextRole.Meta,
                ),
                SupportingInfoSlotRole(
                    slot = SupportingInfoSlot.Value,
                    role = ChromeTextRole.Body,
                ),
                SupportingInfoSlotRole(
                    slot = SupportingInfoSlot.Trailing,
                    role = ChromeTextRole.Body,
                ),
            ),
            content.orderedSlots(),
        )
    }

    @Test
    fun `supporting info label and value are required`() {
        assertThrows(IllegalArgumentException::class.java) {
            SupportingInfoContent(label = "   ", value = "1,689원")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SupportingInfoContent(label = "변동", value = "   ")
        }
    }

    @Test
    fun `row content combines title and value into one title line`() {
        val content = GasStationRowContent(
            title = "찾기 범위",
            value = "3km",
            body = "가장 촘촘하게 주변 가격을 비교합니다.",
            hasLeadingContent = true,
            hasTrailingContent = true,
        )

        assertEquals("찾기 범위 : 3km", content.titleLine)
        assertEquals(
            listOf(
                GasStationRowSlot.Leading,
                GasStationRowSlot.TitleLine,
                GasStationRowSlot.Body,
                GasStationRowSlot.Trailing,
            ),
            content.orderedSlots(),
        )
        assertEquals(
            listOf(
                TextSlotRole(
                    slot = StructuredTextSlot.Title,
                    role = ChromeTextRole.CardTitle,
                ),
                TextSlotRole(
                    slot = StructuredTextSlot.Body,
                    role = ChromeTextRole.Body,
                ),
            ),
            content.orderedTextSlots(),
        )
    }

    @Test
    fun `row title is required and optional text cannot be blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            GasStationRowContent(title = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GasStationRowContent(title = "찾기 범위", value = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GasStationRowContent(title = "찾기 범위", body = "   ")
        }
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
    fun `status banner body cannot be blank when provided`() {
        assertThrows(IllegalArgumentException::class.java) {
            StatusBannerContent(
                title = "오래된 결과를 표시 중입니다.",
                body = "   ",
            )
        }
    }

    @Test
    fun `status banner tone visuals expose renderer consumed colors and marks`() {
        val visuals = GasStationStatusTone.entries.associateWith { tone -> tone.visual() }

        visuals.forEach { (tone, visual) ->
            assertRendererColor("$tone surface", visual.surfaceColor)
            assertRendererColor("$tone border", visual.borderColor)
            assertRendererColor("$tone content", visual.contentColor)
            assertRendererColor("$tone symbol container", visual.symbolContainerColor)
            assertRendererColor("$tone symbol content", visual.symbolContentColor)
            assertTrue(
                "$tone symbol container should be visually distinct from its banner surface.",
                visual.symbolContainerColor != visual.surfaceColor,
            )
            assertTrue(
                "$tone symbol mark should be visible on its container.",
                visual.symbolContentColor != visual.symbolContainerColor,
            )
        }
        assertEquals(
            "Each status tone should have its own surface treatment.",
            GasStationStatusTone.entries.size,
            visuals.values.map { visual -> visual.surfaceColor }.toSet().size,
        )
        assertEquals(
            "Each status tone should draw a distinct decorative mark.",
            GasStationStatusTone.entries.size,
            visuals.values.map { visual -> visual.symbolMark }.toSet().size,
        )
        assertEquals(ColorBlack, visuals.getValue(GasStationStatusTone.Neutral).borderColor)
        assertEquals(ColorSupportInfo, visuals.getValue(GasStationStatusTone.Info).borderColor)
        assertEquals(ColorSupportSuccess, visuals.getValue(GasStationStatusTone.Success).borderColor)
        assertEquals(ColorBlack, visuals.getValue(GasStationStatusTone.Warning).borderColor)
        assertEquals(ColorSupportError, visuals.getValue(GasStationStatusTone.Error).borderColor)
        assertEquals(ColorYellow, visuals.getValue(GasStationStatusTone.Warning).symbolContentColor)
        assertEquals(ColorWhite, visuals.getValue(GasStationStatusTone.Error).symbolContentColor)
    }

    @Test
    fun `guidance card content preserves leading title body and action order`() {
        val content = GuidanceCardContent(
            title = "위치 서비스를 켜야 합니다.",
            body = "GPS 또는 네트워크 위치를 활성화해야 주변 주유소를 불러올 수 있습니다.",
            actionLabel = "위치 설정 열기",
            hasLeadingContent = true,
        )

        assertEquals(
            listOf(
                GuidanceCardSlot.Leading,
                GuidanceCardSlot.Title,
                GuidanceCardSlot.Body,
                GuidanceCardSlot.Action,
            ),
            content.orderedSlots(),
        )
        assertEquals(
            listOf(
                TextSlotRole(
                    slot = StructuredTextSlot.Title,
                    role = ChromeTextRole.SectionTitle,
                ),
                TextSlotRole(
                    slot = StructuredTextSlot.Body,
                    role = ChromeTextRole.Body,
                ),
            ),
            content.orderedTextSlots(),
        )
    }

    @Test
    fun `guidance card title body and optional action label are required`() {
        assertThrows(IllegalArgumentException::class.java) {
            GuidanceCardContent(title = "   ", body = "안내")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GuidanceCardContent(title = "위치 권한이 필요합니다.", body = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GuidanceCardContent(
                title = "위치 권한이 필요합니다.",
                body = "안내",
                actionLabel = "   ",
            )
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

    private fun assertRendererColor(
        name: String,
        color: Color,
    ) {
        assertTrue("$name should be nontransparent.", color.alpha > 0f)
    }
}
