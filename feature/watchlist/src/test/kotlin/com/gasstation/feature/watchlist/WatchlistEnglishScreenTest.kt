package com.gasstation.feature.watchlist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.Brand
import com.gasstation.core.model.FuelType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h800dp-xhdpi")
class WatchlistEnglishScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `English summary and rows use English date and directional delta copy`() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            val item = WatchlistItemUiModel(
                id = "station-1",
                name = "Gangnam First",
                brand = Brand.GSC,
                brandLabel = "GS Caltex",
                priceWon = 1_689,
                priceLabel = "1,689 won",
                priceNumberLabel = "1,689",
                priceUnitLabel = "won",
                distanceLabel = "0.3km",
                distanceNumberLabel = "0.3",
                distanceUnitLabel = "km",
                priceDeltaWon = 14,
                priceDeltaTone = WatchlistPriceDeltaTone.Rise,
                lastSeenAt = Instant.parse("2026-07-17T02:00:00Z"),
                lastSeenLabel = Instant.parse("2026-07-17T02:00:00Z").toWatchlistLastSeenLabel(),
                latitude = 37.49,
                longitude = 127.02,
            )
            composeRule.setContent {
                GasStationTheme {
                    WatchlistScreen(
                        uiState = WatchlistUiState(
                            isLoading = false,
                            fuelType = FuelType.GASOLINE,
                            stations = listOf(item),
                            summary = WatchlistSummaryUiModel.from(listOf(item)),
                        ),
                        onAction = {},
                        onNavigateNearby = {},
                    )
                }
            }

            composeRule.onNodeWithText("Based on Gasoline").assertExists()
            composeRule.onNodeWithText("Last checked Jul 17, 11:00").assertExists()
            composeRule.onNodeWithText("Up 14 won · Jul 17, 11:00").assertExists()
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `English unavailable price uses explicit selected fuel copy`() {
        val item = WatchlistItemUiModel(
            id = "station-1",
            name = "Saved Station",
            brand = Brand.GSC,
            brandLabel = "GS Caltex",
            priceWon = null,
            priceLabel = null,
            priceNumberLabel = null,
            priceUnitLabel = null,
            distanceLabel = "0.3km",
            distanceNumberLabel = "0.3",
            distanceUnitLabel = "km",
            priceDeltaWon = null,
            lastSeenAt = null,
            lastSeenLabel = "-",
            latitude = 37.49,
            longitude = 127.02,
        )
        composeRule.setContent {
            GasStationTheme {
                WatchlistScreen(
                    uiState = WatchlistUiState(
                        isLoading = false,
                        fuelType = FuelType.DIESEL,
                        stations = listOf(item),
                        summary = WatchlistSummaryUiModel.from(listOf(item)),
                    ),
                    onAction = {},
                    onNavigateNearby = {},
                )
            }
        }

        composeRule.onNodeWithText("Based on Diesel").assertExists()
        composeRule.onNodeWithText("Selected fuel price unavailable").assertExists()
    }
}
