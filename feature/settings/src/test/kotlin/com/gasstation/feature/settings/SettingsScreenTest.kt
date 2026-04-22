package com.gasstation.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.unit.dp

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `settings menu rows are laid out vertically`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        val searchRadiusTop = composeRule
            .onNodeWithText("찾기 범위 : ${uiState.selectedLabelFor(SettingsSection.SearchRadius)}")
            .fetchSemanticsNode()
            .boundsInRoot.top
        val fuelTypeTop = composeRule
            .onNodeWithText("오일 타입 : ${uiState.selectedLabelFor(SettingsSection.FuelType)}")
            .fetchSemanticsNode()
            .boundsInRoot.top

        assertTrue("Expected settings rows to stack vertically", fuelTypeTop > searchRadiusTop)
    }

    @Test
    fun `settings menu groups rows under section headings`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        assertTrue(composeRule.onAllNodesWithText("탐색 설정").fetchSemanticsNodes().isNotEmpty())

        val exploreHeadingBounds = composeRule
            .onNodeWithText("탐색 설정", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val searchRadiusBounds = composeRule
            .onNodeWithText(
                "찾기 범위 : ${uiState.selectedLabelFor(SettingsSection.SearchRadius)}",
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot
        composeRule
            .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
            .performScrollToNode(hasText("표시 설정"))
        assertTrue(composeRule.onAllNodesWithText("표시 설정").fetchSemanticsNodes().isNotEmpty())

        val displayHeadingBounds = composeRule
            .onNodeWithText("표시 설정", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val sortOrderBounds = composeRule
            .onNodeWithText(
                "정렬기준 : ${uiState.selectedLabelFor(SettingsSection.SortOrder)}",
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule
            .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
            .performScrollToNode(hasText("연결 설정"))
        assertTrue(composeRule.onAllNodesWithText("연결 설정").fetchSemanticsNodes().isNotEmpty())

        assertTrue(
            "Expected 탐색 설정 heading to appear above its grouped options.",
            exploreHeadingBounds.top < searchRadiusBounds.top,
        )
        assertTrue(
            "Expected 표시 설정 heading to appear above its grouped option.",
            displayHeadingBounds.top < sortOrderBounds.top,
        )
        assertTrue(composeRule.onAllNodesWithText("연결 설정").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `settings menu renders a dedicated group container for each section`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        SettingsSectionGroup.entries.forEach { group ->
            composeRule
                .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
                .performScrollToNode(hasTestTag("$SETTINGS_GROUP_TAG_PREFIX${group.name}"))
            composeRule
                .onNodeWithTag("$SETTINGS_GROUP_TAG_PREFIX${group.name}")
                .assertExists()
        }
    }

    @Test
    fun `settings menu renders flat rows inside each group container`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        SettingsSection.entries.forEach { section ->
            composeRule
                .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
                .performScrollToNode(hasTestTag("$SETTINGS_ROW_TAG_PREFIX${section.routeSegment}"))
            composeRule
                .onNodeWithTag("$SETTINGS_ROW_TAG_PREFIX${section.routeSegment}")
                .assertExists()
        }
    }

    @Test
    fun `settings menu combines option title and current value into one line`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        composeRule.onNodeWithText(
            "찾기 범위 : ${uiState.selectedLabelFor(SettingsSection.SearchRadius)}",
        ).assertExists()
        composeRule.onAllNodesWithText("찾기 범위").assertCountEquals(0)
        composeRule.onAllNodesWithText(uiState.selectedLabelFor(SettingsSection.SearchRadius)).assertCountEquals(0)
    }

    @Test
    fun `settings menu constrains long current values without changing row rhythm`() {
        val uiState = SettingsUiState(
            searchRadius = SearchRadius.KM_5,
            fuelType = FuelType.PREMIUM_GASOLINE,
            brandFilter = BrandFilter.RTX,
            sortOrder = SortOrder.PRICE,
            mapProvider = MapProvider.KAKAO_NAVI,
        )
        val brandRowText = "주유소 브랜드 : ${uiState.selectedLabelFor(SettingsSection.BrandFilter)}"

        composeRule.setContent {
            Box(modifier = Modifier.size(width = 320.dp, height = 720.dp)) {
                SettingsScreen(
                    uiState = uiState,
                    onCloseClick = {},
                    onSectionClick = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(SETTINGS_SCREEN_LIST_TAG)
            .performScrollToNode(hasText(brandRowText))

        val rowBounds = composeRule
            .onNodeWithTag("$SETTINGS_ROW_TAG_PREFIX${SettingsSection.BrandFilter.routeSegment}", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val textBounds = composeRule
            .onNodeWithText(brandRowText, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("Expected long settings value to stay inside its row.", textBounds.right <= rowBounds.right)
        composeRule.onAllNodesWithText(uiState.selectedLabelFor(SettingsSection.BrandFilter)).assertCountEquals(0)
    }

    @Test
    fun `settings detail rows are laid out vertically`() {
        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.SearchRadius,
                options = listOf(
                    SettingOptionUiModel(
                        label = "3km",
                        subtitle = "가장 촘촘하게 주변 가격을 비교합니다.",
                        meta = "현재 선택",
                        action = SettingsAction.SearchRadiusSelected(SearchRadius.KM_3),
                        isSelected = true,
                    ),
                    SettingOptionUiModel(
                        label = "4km",
                        subtitle = "도심과 외곽 사이의 균형을 맞춥니다.",
                        meta = null,
                        action = SettingsAction.SearchRadiusSelected(SearchRadius.KM_4),
                        isSelected = false,
                    ),
                ),
                onBackClick = {},
                onOptionClick = {},
            )
        }

        val firstOptionTop = composeRule.onNodeWithText("3km").fetchSemanticsNode().boundsInRoot.top
        val secondOptionTop = composeRule.onNodeWithText("4km").fetchSemanticsNode().boundsInRoot.top

        assertTrue("Expected detail rows to stack vertically", secondOptionTop > firstOptionTop)
    }

    @Test
    fun `settings detail screen keeps parent group hierarchy inside a single card`() {
        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.SearchRadius,
                options = listOf(
                    SettingOptionUiModel(
                        label = "3km",
                        subtitle = "가장 촘촘하게 주변 가격을 비교합니다.",
                        meta = "현재 선택",
                        action = SettingsAction.SearchRadiusSelected(SearchRadius.KM_3),
                        isSelected = true,
                    ),
                ),
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onAllNodesWithText("찾기 범위").assertCountEquals(1)
        composeRule.onNodeWithText("탐색 설정").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러올 반경을 정합니다.").assertExists()
        composeRule.onNodeWithTag(SETTINGS_OPTIONS_GROUP_TAG).assertExists()
    }

    @Test
    fun `selected detail option relies on a larger check icon instead of current selection text`() {
        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.SearchRadius,
                options = listOf(
                    SettingOptionUiModel(
                        label = "3km",
                        subtitle = "가장 촘촘하게 주변 가격을 비교합니다.",
                        meta = "현재 선택",
                        action = SettingsAction.SearchRadiusSelected(SearchRadius.KM_3),
                        isSelected = true,
                    ),
                ),
                onBackClick = {},
                onOptionClick = {},
            )
        }

        val subtitleBounds = composeRule
            .onNodeWithText("가장 촘촘하게 주변 가격을 비교합니다.", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onAllNodesWithText("현재 선택").assertCountEquals(0)
        composeRule
            .onNodeWithTag(SETTINGS_SELECTED_CHECK_TAG, useUnmergedTree = true)
            .assertHeightIsAtLeast(24.dp)

        val checkBounds = composeRule
            .onNodeWithTag(SETTINGS_SELECTED_CHECK_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected selected check icon to stay above the descriptive subtitle.",
            checkBounds.top < subtitleBounds.top,
        )
    }

    @Test
    fun `brand filter detail renders icons for concrete brands only`() {
        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.BrandFilter,
                options = listOf(
                    SettingOptionUiModel(
                        label = "전체",
                        subtitle = "브랜드 제한 없이 가까운 가격을 한 번에 확인합니다.",
                        meta = "현재 선택",
                        action = SettingsAction.BrandFilterSelected(BrandFilter.ALL),
                        isSelected = true,
                        brandIconBrand = null,
                    ),
                    SettingOptionUiModel(
                        label = "GS칼텍스",
                        subtitle = "GS칼텍스 주유소만 골라 비교합니다.",
                        meta = null,
                        action = SettingsAction.BrandFilterSelected(BrandFilter.GSC),
                        isSelected = false,
                        brandIconBrand = Brand.GSC,
                    ),
                ),
                onBackClick = {},
                onOptionClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("GS칼텍스 브랜드").assertExists()
        composeRule.onNodeWithContentDescription("전체 브랜드").assertDoesNotExist()
    }

    @Test
    fun `brand filter all option aligns with concrete brand rows`() {
        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.BrandFilter,
                options = listOf(
                    SettingOptionUiModel(
                        label = "전체",
                        subtitle = "브랜드 제한 없이 가까운 가격을 한 번에 확인합니다.",
                        meta = "현재 선택",
                        action = SettingsAction.BrandFilterSelected(BrandFilter.ALL),
                        isSelected = true,
                        brandIconBrand = null,
                    ),
                    SettingOptionUiModel(
                        label = "SK에너지",
                        subtitle = "SK에너지 주유소만 골라 비교합니다.",
                        meta = null,
                        action = SettingsAction.BrandFilterSelected(BrandFilter.SKE),
                        isSelected = false,
                        brandIconBrand = Brand.SKE,
                    ),
                ),
                onBackClick = {},
                onOptionClick = {},
            )
        }

        val allLabelLeft = composeRule
            .onNodeWithText("전체", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .left
        val skEnergyLabelLeft = composeRule
            .onNodeWithText("SK에너지", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .left

        assertEquals(
            "Expected 전체 to reserve the same leading slot as concrete brand rows.",
            skEnergyLabelLeft,
            allLabelLeft,
            1f,
        )
    }
}
