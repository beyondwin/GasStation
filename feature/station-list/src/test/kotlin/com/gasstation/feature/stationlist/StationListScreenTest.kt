package com.gasstation.feature.stationlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
