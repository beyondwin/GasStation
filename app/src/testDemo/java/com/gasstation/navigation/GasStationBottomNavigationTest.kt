package com.gasstation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.gasstation.core.designsystem.GasStationTheme
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
                    SemanticsProperties.StateDescription,
                    "현재 위치 확인 후 사용 가능",
                ),
            )
    }
}
