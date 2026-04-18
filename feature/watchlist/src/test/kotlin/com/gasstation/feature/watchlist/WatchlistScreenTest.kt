package com.gasstation.feature.watchlist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `watchlist uses bookmark copy across title and empty state`() {
        composeRule.setContent {
            WatchlistScreen(
                uiState = WatchlistUiState(
                    stations = emptyList(),
                ),
            )
        }

        composeRule.onNodeWithText("북마크").assertExists()
        composeRule.onNodeWithText("저장한 주유소가 없습니다.").assertExists()
        composeRule.onNodeWithText("주유소 목록에서 북마크를 눌러 가격과 거리를 한곳에 모아보세요.").assertExists()
        composeRule.onNodeWithText("목록 화면에서 북마크를 눌러 바로 추가하세요.").assertExists()
        composeRule.onNodeWithText("저장한 주유소의 가격과 거리를 한 번에 비교합니다.").assertExists()
    }

    @Test
    fun `watchlist shows delta indicator to the right of change value`() {
        composeRule.setContent {
            WatchlistScreen(
                uiState = WatchlistUiState(
                    stations = listOf(
                        WatchlistItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brandLabel = "GS칼텍스",
                            priceLabel = "2,022원",
                            priceNumberLabel = "2,022",
                            priceUnitLabel = "원",
                            distanceLabel = "0.3km",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "17원",
                            priceDeltaTone = WatchlistPriceDeltaTone.Rise,
                            lastSeenLabel = "4월 18일 12:00",
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                ),
            )
        }

        val changeValueBounds = composeRule
            .onNodeWithTag(WATCHLIST_CHANGE_VALUE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val deltaIndicatorBounds = composeRule
            .onNodeWithTag(WATCHLIST_DELTA_INDICATOR_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(deltaIndicatorBounds.left > changeValueBounds.right)
        assertEquals(changeValueBounds.top, deltaIndicatorBounds.top, 2f)
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
        priceDeltaLabel = "-",
        lastSeenLabel = "4월 18일 12:00",
        latitude = 37.498095,
        longitude = 127.02761,
    )
}
