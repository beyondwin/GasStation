package com.gasstation.feature.stationlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.domain.station.model.FuelType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `station card surfaces price above station name on the reference screen`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brandLabel = "GS칼텍스",
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "직전 가격과 동일",
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        val priceTop = composeRule
            .onNodeWithTag(STATION_LIST_METRIC_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val titleTop = composeRule
            .onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top

        assertTrue(
            "Expected the price hero to appear above the station title (priceTop=$priceTop, titleTop=$titleTop)",
            priceTop < titleTop,
        )
    }

    @Test
    fun `station card aligns distance label height with price label height`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brandLabel = "GS칼텍스",
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "직전 가격과 동일",
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        val priceLabelTop = composeRule
            .onNodeWithText("가격", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val distanceLabelTop = composeRule
            .onNodeWithText("거리", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top

        assertEquals(
            "Expected distance label to share the same top position as price label.",
            priceLabelTop,
            distanceLabelTop,
            1f,
        )
    }

    @Test
    fun `station card places price comparison to the right of fuel and brand row`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brandLabel = "GS칼텍스",
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "17원",
                            priceDeltaTone = PriceDeltaTone.Fall,
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        val brandRight = composeRule
            .onNodeWithText("GS칼텍스", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.right
        val brandTop = composeRule
            .onNodeWithText("GS칼텍스", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val priceComparisonBounds = composeRule
            .onNodeWithTag(STATION_LIST_PRICE_CHANGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected price comparison text to appear to the right of brand row.",
            priceComparisonBounds.left > brandRight,
        )
        assertEquals(
            "Expected price comparison text to share the same row as fuel and brand.",
            brandTop,
            priceComparisonBounds.top,
            1f,
        )
    }

    @Test
    fun `loading keeps rendered station list visible while showing progress copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brandLabel = "GS칼텍스",
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "직전 가격과 동일",
                            isWatched = true,
                            latitude = 37.498095,
                            longitude = 127.02761,
                        ),
                    ),
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertExists()
    }

    @Test
    fun `top bar watchlist action uses bookmark copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
                onWatchlistClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("북마크").assertExists()
    }
}
