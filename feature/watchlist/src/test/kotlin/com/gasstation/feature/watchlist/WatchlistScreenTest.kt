package com.gasstation.feature.watchlist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchlistScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `watchlist cards expose stable semantics hook`() {
        composeRule.setContent {
            WatchlistScreen(
                uiState = WatchlistUiState(
                    stations = listOf(
                        WatchlistItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brandLabel = "GS칼텍스",
                            priceLabel = "1689원",
                            priceNumberLabel = "1689",
                            priceUnitLabel = "원",
                            distanceLabel = "300m",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "직전 가격과 동일",
                            lastSeenLabel = "4월 18일 12:00",
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithTag(
            WATCHLIST_CARD_CONTENT_DESCRIPTION,
            useUnmergedTree = true,
        )
            .assertCountEquals(1)
    }

    @Test
    fun `watchlist keeps comparison metric columns aligned across cards`() {
        composeRule.setContent {
            WatchlistScreen(
                uiState = WatchlistUiState(
                    stations = listOf(
                        watchlistStation(
                            id = "station-1",
                            name = "가까운 주유소",
                            priceNumberLabel = "999",
                        ),
                        watchlistStation(
                            id = "station-2",
                            name = "조금 먼 주유소",
                            priceNumberLabel = "1,899",
                        ),
                    ),
                ),
            )
        }

        val distanceMetricNodes = composeRule
            .onAllNodesWithTag(WATCHLIST_DISTANCE_METRIC_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()

        assertEquals(2, distanceMetricNodes.size)
        assertEquals(
            distanceMetricNodes[0].boundsInRoot.left.toDouble(),
            distanceMetricNodes[1].boundsInRoot.left.toDouble(),
            0.5,
        )
    }

    private fun watchlistStation(
        id: String,
        name: String,
        priceNumberLabel: String,
    ) = WatchlistItemUiModel(
        id = id,
        name = name,
        brandLabel = "GS칼텍스",
        priceLabel = "${priceNumberLabel}원",
        priceNumberLabel = priceNumberLabel,
        priceUnitLabel = "원",
        distanceLabel = "0.3km",
        distanceNumberLabel = "0.3",
        distanceUnitLabel = "km",
        priceDeltaLabel = "직전 가격과 동일",
        lastSeenLabel = "4월 18일 12:00",
        latitude = 37.498095,
        longitude = 127.02761,
    )
}
