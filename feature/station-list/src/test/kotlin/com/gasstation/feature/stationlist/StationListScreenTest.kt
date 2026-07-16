package com.gasstation.feature.stationlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.gasstation.core.model.Brand
import com.gasstation.core.model.FuelType
import com.gasstation.domain.location.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko")
class StationListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `query context shows current address only through dong and condition without old card copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    currentAddressLabel = "서울 영등포구 당산동",
                    stations = listOf(testStation()),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG).assertExists()
        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG).assertExists()
        composeRule.onNodeWithText("서울 영등포구 당산동").assertExists()
        composeRule.onNodeWithText("3km · 휘발유 기준").assertExists()
        composeRule.onNodeWithText("현재 조건").assertDoesNotExist()
        composeRule.onNodeWithText("반경과 유종 기준으로 정렬합니다.").assertDoesNotExist()
    }

    @Test
    fun `query context does not treat building dong as administrative dong`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    currentAddressLabel = "서울특별시 강남구 역삼동",
                    stations = listOf(testStation()),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("서울특별시 강남구 역삼동").assertExists()
    }

    @Test
    fun `query context shows condition when current address is unavailable`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    currentAddressLabel = null,
                    stations = listOf(testStation()),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_TAG).assertExists()
        composeRule.onNodeWithTag(STATION_LIST_QUERY_CONTEXT_LOCATION_ICON_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("3km · 휘발유 기준").assertExists()
        composeRule.onNodeWithText("현재 조건").assertDoesNotExist()
        composeRule.onNodeWithText("반경과 유종 기준으로 정렬합니다.").assertDoesNotExist()
    }

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
                            brand = Brand.GSC,
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
                            brand = Brand.GSC,
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
    fun `station card places price comparison to the right of fuel and brand icon row`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
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

        val brandIconRight = composeRule
            .onNodeWithContentDescription("GS칼텍스 브랜드", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.right
        val priceComparisonBounds = composeRule
            .onNodeWithTag(STATION_LIST_PRICE_CHANGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected price comparison text to appear to the right of brand icon row.",
            priceComparisonBounds.left > brandIconRight,
        )
    }

    @Test
    fun `station card constrains long names prices and fuel labels on narrow width`() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 320.dp, height = 720.dp)) {
                StationListScreen(
                    uiState = StationListUiState(
                        permissionState = LocationPermissionState.PreciseGranted,
                        stations = listOf(
                            StationListItemUiModel(
                                id = "station-1",
                                name = "서울특별시강남구테헤란로초장문테스트주유소직영점",
                                brand = Brand.HDO,
                                brandLabel = "현대오일뱅크",
                                priceLabel = "123,456,789원",
                                distanceLabel = "123.4km",
                                priceNumberLabel = "123,456,789",
                                priceUnitLabel = "원",
                                distanceNumberLabel = "123.4",
                                distanceUnitLabel = "km",
                                priceDeltaLabel = "999원",
                                priceDeltaTone = PriceDeltaTone.Fall,
                                isWatched = false,
                                latitude = 37.498095,
                                longitude = 127.02761,
                            ),
                        ),
                        selectedFuelType = FuelType.PREMIUM_GASOLINE,
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onAction = {},
                    onRequestPermissions = {},
                    onOpenLocationSettings = {},
                    onSettingsClick = {},
                )
            }
        }

        val rootRight = composeRule.onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .right
        val metricBounds = composeRule
            .onNodeWithTag(STATION_LIST_METRIC_ROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithTag(STATION_LIST_CARD_TITLE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val fuelChipBounds = composeRule
            .onNodeWithTag(STATION_LIST_FUEL_CHIP_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val brandIconBounds = composeRule
            .onNodeWithContentDescription("현대오일뱅크 브랜드", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val priceComparisonBounds = composeRule
            .onNodeWithTag(STATION_LIST_PRICE_CHANGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("Expected metric row to stay inside the narrow screen.", metricBounds.right <= rootRight)
        assertTrue("Expected station title to stay inside the narrow screen.", titleBounds.right <= rootRight)
        assertTrue("Expected fuel chip to leave room for the brand icon.", fuelChipBounds.right < brandIconBounds.left)
        assertTrue("Expected brand icon to stay before the price delta.", brandIconBounds.right < priceComparisonBounds.left)
    }

    @Test
    fun `station card renders brand icon without visible brand label`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(
                        StationListItemUiModel(
                            id = "station-1",
                            name = "테스트 주유소",
                            brand = Brand.GSC,
                            brandLabel = "GS칼텍스",
                            priceLabel = "1,689원",
                            distanceLabel = "0.3km",
                            priceNumberLabel = "1,689",
                            priceUnitLabel = "원",
                            distanceNumberLabel = "0.3",
                            distanceUnitLabel = "km",
                            priceDeltaLabel = "-",
                            isWatched = false,
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

        composeRule.onNodeWithContentDescription("GS칼텍스 브랜드").assertExists()
        composeRule.onNodeWithText("GS칼텍스").assertDoesNotExist()
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
                            brand = Brand.GSC,
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
                            brand = Brand.GSC,
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
        composeRule.onNodeWithText("현재 조건으로 최신 가격을 확인하고 있습니다.").assertExists()
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
                            brand = Brand.GSC,
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
    fun `stale and approximate banners render as guidance without hiding cached results`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.ApproximateGranted,
                    isStale = true,
                    lastUpdatedAt = Instant.parse("2026-04-18T00:30:00Z"),
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

        composeRule.onNodeWithText("대략적인 위치 기준입니다.").assertExists()
        composeRule.onNodeWithText("정확한 거리 비교가 필요하면 위치 권한을 정확도로 바꿔주세요.").assertExists()
        composeRule.onNodeWithText("저장된 결과를 표시 중입니다.").assertExists()
        composeRule.onNodeWithText("테스트 주유소").assertExists()
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
    fun `permission required state wins over initial loading guidance`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.Denied,
                    hasDeniedLocationAccess = false,
                    isLoading = true,
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
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
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
    fun `gps required state renders location settings guidance and opens settings`() {
        var locationSettingsOpened = false

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isGpsEnabled = false,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = { locationSettingsOpened = true },
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("위치 서비스를 켜야 합니다.").assertExists()
        composeRule.onNodeWithText(
            "GPS 또는 네트워크 위치를 활성화해야 주변 주유소와 북마크를 정확하게 불러올 수 있습니다.",
        ).assertExists()
        composeRule.onNodeWithText("위치 설정 열기").performClick()

        assertTrue(locationSettingsOpened)
    }

    @Test
    fun `gps required state wins over initial loading guidance`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isGpsEnabled = false,
                    isLoading = true,
                    selectedFuelType = FuelType.DIESEL,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
            )
        }

        composeRule.onNodeWithText("위치 서비스를 켜야 합니다.").assertExists()
        composeRule.onNodeWithText("주변 주유소를 불러오는 중입니다.").assertDoesNotExist()
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
        composeRule.onNodeWithText("서버 응답이 늦습니다. 잠시 후 같은 조건으로 다시 시도해주세요.").assertExists()
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
    fun `station list exposes stable benchmark tags without changing copy`() {
        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    stations = listOf(testStation()),
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
        composeRule.onNodeWithTag(STATION_LIST_WATCHLIST_ACTION_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("저장").assertExists()
        composeRule.onNodeWithTag(STATION_LIST_WATCH_TOGGLE_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `empty results state renders empty guidance and retry action`() {
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

        composeRule.onNodeWithText("조건에 맞는 주변 주유소가 없습니다.").assertExists()
        composeRule.onNodeWithText("반경, 유종, 브랜드 조건을 조정하거나 다시 조회해보세요.").assertExists()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(listOf(StationListAction.RetryClicked), actions)
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
        composeRule.onNodeWithTag(STATION_LIST_ROOT_TAG, useUnmergedTree = true).assertExists()
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

    @Test
    fun `first content callback waits during initial loading`() {
        var callbackCount = 0

        composeRule.setContent {
            StationListScreen(
                uiState = StationListUiState(
                    permissionState = LocationPermissionState.PreciseGranted,
                    isLoading = true,
                    stations = emptyList(),
                    selectedFuelType = FuelType.GASOLINE,
                ),
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
                onFirstContentDrawn = { callbackCount += 1 },
            )
        }

        composeRule.waitForIdle()

        assertEquals(0, callbackCount)
    }

    @Test
    fun `first content callback fires once after usable station content appears`() {
        var callbackCount = 0
        var uiState by mutableStateOf(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = emptyList(),
                selectedFuelType = FuelType.GASOLINE,
            ),
        )

        composeRule.setContent {
            StationListScreen(
                uiState = uiState,
                snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                onAction = {},
                onRequestPermissions = {},
                onOpenLocationSettings = {},
                onSettingsClick = {},
                onFirstContentDrawn = { callbackCount += 1 },
            )
        }

        composeRule.waitForIdle()
        assertEquals(0, callbackCount)

        composeRule.runOnUiThread {
            uiState = uiState.copy(
                isLoading = false,
                stations = listOf(testStation()),
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            uiState = uiState.copy(isRefreshing = true)
        }
        composeRule.waitForIdle()

        assertEquals(1, callbackCount)
    }

    private fun testStation() = StationListItemUiModel(
        id = "station-1",
        name = "테스트 주유소",
        brand = Brand.GSC,
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
