package com.gasstation.feature.watchlist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
}
