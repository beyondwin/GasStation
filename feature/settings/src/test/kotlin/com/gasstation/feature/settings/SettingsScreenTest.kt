package com.gasstation.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
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
    fun `settings detail rows are laid out vertically`() {
        composeRule.setContent {
            SettingsDetailScreen(
                section = SettingsSection.SearchRadius,
                options = listOf(
                    SettingOptionUiModel(
                        label = "3km",
                        action = SettingsAction.SearchRadiusSelected(SearchRadius.KM_3),
                        isSelected = true,
                    ),
                    SettingOptionUiModel(
                        label = "4km",
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
}
