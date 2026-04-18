package com.gasstation.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.SearchRadius
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `settings menu rows are laid out vertically`() {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState.from(UserPreferences.default()),
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        val searchRadiusTop = composeRule.onNodeWithText("찾기 범위").fetchSemanticsNode().boundsInRoot.top
        val fuelTypeTop = composeRule.onNodeWithText("오일 타입").fetchSemanticsNode().boundsInRoot.top

        assertTrue("Expected settings rows to stack vertically", fuelTypeTop > searchRadiusTop)
    }

    @Test
    fun `settings menu rows show overline subtitle and current selection in one hierarchy`() {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState.from(UserPreferences.default()),
                onCloseClick = {},
                onSectionClick = {},
            )
        }

        composeRule.onAllNodesWithText("탐색 설정").assertCountEquals(3)
        composeRule.onNodeWithText("주변 주유소를 불러올 반경을 정합니다.").assertExists()
        composeRule.onNodeWithText("현재 설정: 3km").assertExists()
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
    fun `selected detail option keeps stable hierarchy with descriptive subtitle and meta`() {
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
        composeRule.onNodeWithText("가장 촘촘하게 주변 가격을 비교합니다.").assertExists()
        composeRule.onNodeWithText("현재 선택").assertExists()
    }
}
