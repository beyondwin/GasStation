package com.gasstation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.GasStationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko")
class GasStationBottomNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `bottom navigation is icon only while preserving destination accessibility`() {
        composeRule.setContent {
            GasStationTheme {
                GasStationBottomNavigation(
                    selected = TopLevelDestination.Settings,
                    watchlistEnabled = true,
                    onNearby = {},
                    onWatchlist = {},
                    onSettings = {},
                )
            }
        }

        listOf("주변", "관심", "설정").forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        }

        assertDestination(
            tag = BOTTOM_NAV_NEARBY_TAG,
            label = "주변",
            selected = false,
        )
        assertDestination(
            tag = BOTTOM_NAV_WATCHLIST_TAG,
            label = "관심",
            selected = false,
        )
        assertDestination(
            tag = BOTTOM_NAV_SETTINGS_TAG,
            label = "설정",
            selected = true,
        )
    }

    @Test
    fun `bottom navigation exposes stable tags and disabled watchlist explanation`() {
        composeRule.setContent {
            GasStationTheme {
                GasStationBottomNavigation(
                    selected = TopLevelDestination.Nearby,
                    watchlistEnabled = false,
                    onNearby = {},
                    onWatchlist = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(BOTTOM_NAV_NEARBY_TAG).assertExists()
        composeRule.onNodeWithTag(BOTTOM_NAV_SETTINGS_TAG).assertExists()
        composeRule.onNodeWithTag(BOTTOM_NAV_WATCHLIST_TAG)
            .assertExists()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("관심"),
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "현재 위치 확인 후 사용 가능",
                ),
            )
    }

    private fun assertDestination(tag: String, label: String, selected: Boolean) {
        val node = composeRule.onNodeWithTag(tag)
            .assertExists()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(label),
                ),
            )

        if (selected) {
            node.assertIsSelected()
        } else {
            node.assertIsNotSelected()
        }

        val bounds = node.fetchSemanticsNode().boundsInRoot
        with(composeRule.density) {
            assertTrue(
                "Expected $tag touch width to be at least 48dp, was ${bounds.width.toDp()}",
                bounds.width.toDp() >= 48.dp,
            )
            assertTrue(
                "Expected $tag touch height to be at least 48dp, was ${bounds.height.toDp()}",
                bounds.height.toDp() >= 48.dp,
            )
        }
    }
}
