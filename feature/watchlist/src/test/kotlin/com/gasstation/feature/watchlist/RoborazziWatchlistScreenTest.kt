package com.gasstation.feature.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.gasStationBrandLabel
import com.gasstation.core.designsystem.gasStationPriceDigits
import com.gasstation.core.designsystem.gasStationPriceLabel
import com.gasstation.core.model.Brand
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MoneyWon
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziWatchlistScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun five_saved_comparison_rows() {
        renderFiveRows("watchlist-five-rows.png")
    }

    @Test
    fun five_saved_comparison_rows_dark() {
        renderFiveRows("watchlist-five-rows-dark.png", darkTheme = true)
    }

    private fun renderFiveRows(name: String, darkTheme: Boolean = false) {
        composeRule.setContent {
            GasStationTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 800.dp)
                        .testTag(WATCHLIST_SNAPSHOT_TAG),
                ) {
                    WatchlistScreen(
                        uiState = fiveRowState(),
                        onAction = {},
                        onNavigateNearby = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag(WATCHLIST_ROW_TAG, useUnmergedTree = true).assertCountEquals(5)
        composeRule.onNodeWithContentDescription("자영알뜰 브랜드").assertExists()
        composeRule.onNodeWithContentDescription("자가상표 브랜드").assertExists()
        composeRule.onNodeWithTag(WATCHLIST_SNAPSHOT_TAG, useUnmergedTree = true)
            .captureRoboImage("src/test/snapshots/$name")
    }

    @Test
    fun empty_saved_comparison() {
        renderEmpty("watchlist-empty.png")
    }

    @Test
    fun empty_saved_comparison_dark() {
        renderEmpty("watchlist-empty-dark.png", darkTheme = true)
    }

    private fun renderEmpty(name: String, darkTheme: Boolean = false) {
        composeRule.setContent {
            GasStationTheme(darkTheme = darkTheme) {
                WatchlistScreen(
                    uiState = WatchlistUiState(
                        isLoading = false,
                        fuelType = FuelType.GASOLINE,
                    ),
                    onAction = {},
                    onNavigateNearby = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name")
    }

    private fun fiveRowState(): WatchlistUiState {
        val items = listOf(Brand.SKE, Brand.GSC, Brand.SOL, Brand.RTO, Brand.ETC)
            .mapIndexed { index, brand ->
                val price = 1_680 + index * 10
                WatchlistItemUiModel(
                    id = "station-${index + 1}",
                    name = if (index == 0) "강남 제일 주유소" else "비교 주유소 ${index + 1}",
                    brand = brand,
                    brandLabel = brand.gasStationBrandLabel(),
                    priceWon = price,
                    priceLabel = MoneyWon(price).gasStationPriceLabel(),
                    priceNumberLabel = MoneyWon(price).gasStationPriceDigits(),
                    priceUnitLabel = "원",
                    distanceLabel = "${index + 1}.0km",
                    distanceNumberLabel = "${index + 1}.0",
                    distanceUnitLabel = "km",
                    priceDeltaWon = null,
                    lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"),
                    lastSeenLabel = "7월 17일 11:00",
                    latitude = 37.49 + index * 0.001,
                    longitude = 127.02 + index * 0.001,
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

private const val WATCHLIST_SNAPSHOT_TAG = "watchlist-snapshot"
