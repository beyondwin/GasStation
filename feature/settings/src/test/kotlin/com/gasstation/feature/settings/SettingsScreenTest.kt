package com.gasstation.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.SearchRadius
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
    fun `settings menu rows combine section title and selected value into one left aligned line`() {
        val uiState = SettingsUiState.from(UserPreferences.default())

        composeRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        composeRule.onAllNodesWithText("탐색 설정").assertCountEquals(3)

        val combinedTitleBounds = composeRule
            .onNodeWithText(
                "찾기 범위 : ${uiState.selectedLabelFor(SettingsSection.SearchRadius)}",
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot
        val subtitleBounds = composeRule
            .onNodeWithText("주변 주유소를 불러올 반경을 정합니다.", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected combined title/value text to appear above the descriptive subtitle.",
            combinedTitleBounds.top < subtitleBounds.top,
        )
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

        composeRule.onAllNodesWithText("찾기 범위").assertCountEquals(2)

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
}
