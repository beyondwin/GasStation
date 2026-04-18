package com.gasstation.feature.stationlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.station.model.FuelType
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
    fun `loading keeps rendered station list visible while showing top refresh rail`() {
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
        composeRule.onNodeWithText("가격 갱신 중").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `refreshing keeps rendered station list visible without overlay loading card`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isRefreshing = true,
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
        composeRule.onNodeWithText("가격 갱신 중").assertExists()
        composeRule.onNodeWithText("현재 조건 기준 최신 가격을 확인하고 있습니다.").assertExists()
        composeRule.onNodeWithText("새로고침 중입니다.").assertDoesNotExist()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `refreshing state wins over loading overlay when cached results are visible`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    isRefreshing = true,
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
        composeRule.onNodeWithText("가격 갱신 중").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
    }

    @Test
    fun `blocking failure renders retryable failure card instead of empty results copy`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.LocationTimedOut,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("위치를 확인하는 데 시간이 오래 걸리고 있습니다.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(listOf(StationListAction.RetryClicked), actions)
    }

    @Test
    fun `cached results stay visible when blocking failure is null`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
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
        composeRule.onNodeWithText("위치를 확인하는 데 시간이 오래 걸리고 있습니다.").assertDoesNotExist()
    }

    @Test
    fun `cached results stay visible when blocking failure exists`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.RefreshFailed,
                    stations = listOf(testStation()),
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
        composeRule.onNodeWithText("테스트 주유소").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertDoesNotExist()
        composeRule.onNodeWithText("네트워크 또는 서버 상태를 확인한 뒤 다시 시도해주세요.").assertDoesNotExist()
    }

    @Test
    fun `location failure shows generic location failure copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.LocationFailed,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("현재 위치를 확인하지 못했습니다.").assertExists()
        composeRule.onNodeWithText("위치 권한과 위치 서비스 상태를 확인한 뒤 다시 시도해주세요.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
    }

    @Test
    fun `denied permission with stale coordinates still shows permission required state`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    currentCoordinates = com.gasstation.core.model.Coordinates(37.498095, 127.02761),
                    permissionState = LocationPermissionState.Denied,
                    hasDeniedLocationAccess = false,
                    stations = listOf(testStation()),
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("위치 권한이 필요합니다.").assertExists()
        composeRule.onNodeWithText("테스트 주유소").assertDoesNotExist()
    }

    @Test
    fun `denied permission without bypass shows permission required instead of stale failure`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.Denied,
                    hasDeniedLocationAccess = false,
                    blockingFailure = StationListFailureReason.LocationFailed,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("위치 권한이 필요합니다.").assertExists()
        composeRule.onNodeWithText(
            "주변 주유소를 찾고 거리순과 가격순 정렬을 사용하려면 위치 접근을 허용해주세요.",
        ).assertExists()
        composeRule.onNodeWithText("현재 위치를 확인하지 못했습니다.").assertDoesNotExist()
    }

    @Test
    fun `refresh timeout shows slow server failure copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.RefreshTimedOut,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertExists()
        composeRule.onNodeWithText("서버 응답이 늦어 주변 주유소를 아직 불러오지 못했습니다. 잠시 후 다시 시도해주세요.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
    }

    @Test
    fun `refresh failure shows generic network failure copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    blockingFailure = StationListFailureReason.RefreshFailed,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("주변 주유소를 불러오지 못했습니다.").assertExists()
        composeRule.onNodeWithText("네트워크 또는 서버 상태를 확인한 뒤 다시 시도해주세요.").assertExists()
        composeRule.onNodeWithText("주변 주유소가 없습니다.").assertDoesNotExist()
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

    @Test
    fun `pull to refresh on populated results requests refresh`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_PULL_REFRESH_TAG, useUnmergedTree = true)
            .performTouchInput { swipeDown() }

        assertEquals(listOf(StationListAction.RefreshRequested), actions)
    }

    @Test
    fun `pull to refresh on empty results requests refresh`() {
        val actions = mutableListOf<StationListAction>()

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = actions::add,
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_PULL_REFRESH_TAG, useUnmergedTree = true)
            .performTouchInput { swipeDown() }

        assertEquals(listOf(StationListAction.RefreshRequested), actions)
    }

    private fun testStation() = StationListItemUiModel(
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
    )
}
