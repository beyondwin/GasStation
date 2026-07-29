package com.gasstation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.model.Brand
import com.gasstation.core.model.FuelType
import com.gasstation.feature.watchlist.WatchlistItemUiModel
import com.gasstation.feature.watchlist.WatchlistScreen
import com.gasstation.feature.watchlist.WatchlistSummaryUiModel
import com.gasstation.feature.watchlist.WatchlistUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
class GasStationRootWatchlistLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `root scaffold consumes the bottom inset already handled by bottom navigation`() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    GasStationRootScaffold(
                        bottomBar = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .testTag("inset-probe-bottom-bar"),
                            )
                        },
                    ) { rootPadding ->
                        InsetProbeScaffold(
                            modifier = Modifier.padding(rootPadding),
                        )
                    }
                }
            }
        }

        val probeBounds = composeRule.onNodeWithTag("inset-probe-content").fetchSemanticsNode().boundsInRoot
        val bottomBarTop = composeRule.onNodeWithTag("inset-probe-bottom-bar").fetchSemanticsNode().boundsInRoot.top

        with(composeRule.density) {
            assertEquals(24.dp, probeBounds.top.toDp())
            assertEquals(bottomBarTop.toDp(), probeBounds.bottom.toDp())
        }
    }

    @Test
    fun `root scaffold preserves system insets when bottom navigation is absent`() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    GasStationRootScaffold(bottomBar = {}) { rootPadding ->
                        InsetProbeScaffold(
                            modifier = Modifier.padding(rootPadding),
                        )
                    }
                }
            }
        }

        val probeBounds = composeRule.onNodeWithTag("inset-probe-content").fetchSemanticsNode().boundsInRoot

        with(composeRule.density) {
            assertEquals(24.dp, probeBounds.top.toDp())
            assertEquals(752.dp, probeBounds.bottom.toDp())
        }
    }

    @Test
    fun `root scaffold keeps five complete dense rows above bottom navigation`() {
        composeRule.setContent {
            GasStationTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    GasStationRootScaffold(
                        bottomBar = {
                            GasStationBottomNavigation(
                                selected = TopLevelDestination.Watchlist,
                                watchlistEnabled = true,
                                onNearby = {},
                                onWatchlist = {},
                                onSettings = {},
                            )
                        },
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            WatchlistScreen(
                                uiState = fiveRowState(),
                                onAction = {},
                                onNavigateNearby = {},
                            )
                        }
                    }
                }
            }
        }

        val rows = composeRule.onAllNodesWithTag("watchlist-card", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(5, rows.size)
        val heights = rows.map { with(composeRule.density) { it.boundsInRoot.height.toDp() } }
        assertTrue("Expected root rows in 108..116dp, got $heights", heights.all { it in 108.dp..116.dp })
        val bottomNavigationTop = composeRule.onNodeWithTag(BOTTOM_NAV_WATCHLIST_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Expected fifth row above bottom navigation, row=${rows.last().boundsInRoot}, navTop=$bottomNavigationTop",
            rows.last().boundsInRoot.bottom <= bottomNavigationTop,
        )
    }

    @Composable
    private fun InsetProbeScaffold(modifier: Modifier = Modifier) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(top = 24.dp, bottom = 48.dp),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("inset-probe-content"),
            )
        }
    }

    private fun fiveRowState(): WatchlistUiState {
        val items = listOf(Brand.SKE, Brand.GSC, Brand.SOL, Brand.RTO, Brand.ETC).mapIndexed { index, brand ->
            WatchlistItemUiModel(
                id = "station-${index + 1}",
                name = "비교 주유소 ${index + 1}",
                brand = brand,
                brandLabel = brand.gasStationBrandLabel(),
                priceWon = 1_680 + index * 10,
                priceLabel = "${1_680 + index * 10}원",
                priceNumberLabel = "${1_680 + index * 10}",
                priceUnitLabel = "원",
                distanceLabel = "${index + 1}.0km",
                distanceNumberLabel = "${index + 1}.0",
                distanceUnitLabel = "km",
                priceDeltaWon = null,
                lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"),
                lastSeenLabel = "7월 17일 11:00",
                latitude = 37.49,
                longitude = 127.02,
            )
        }
        return WatchlistUiState(
            isLoading = false,
            fuelType = FuelType.GASOLINE,
            stations = items,
            summary = WatchlistSummaryUiModel.from(items),
        )
    }
}
