package com.gasstation.feature.stationlist

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziStationListScreenTest {
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

    private val cachedStations = listOf(
        StationListItemUiModel(
            id = "station-1",
            name = "SK에너지 강남점",
            brand = Brand.SKE,
            brandLabel = "SK에너지",
            priceWon = 1_712,
            priceLabel = "1,712원",
            distanceLabel = "1.2km",
            priceNumberLabel = "1,712",
            priceUnitLabel = "원",
            distanceNumberLabel = "1.2",
            distanceUnitLabel = "km",
            priceHistory = StationListPriceHistoryUiModel.Unavailable,
            isWatched = false,
            latitude = 37.497942,
            longitude = 127.027621,
        ),
        StationListItemUiModel(
            id = "station-2",
            name = "고속도로알뜰 서초점",
            brand = Brand.RTX,
            brandLabel = "고속도로알뜰",
            priceWon = 1_724,
            priceLabel = "1,724원",
            distanceLabel = "1.6km",
            priceNumberLabel = "1,724",
            priceUnitLabel = "원",
            distanceNumberLabel = "1.6",
            distanceUnitLabel = "km",
            priceHistory = StationListPriceHistoryUiModel.Unchanged,
            isWatched = true,
            latitude = 37.500123,
            longitude = 127.036540,
        ),
        StationListItemUiModel(
            id = "station-3",
            name = "우리동네 셀프주유소",
            brand = Brand.ETC,
            brandLabel = "자가상표",
            priceWon = 1_746,
            priceLabel = "1,746원",
            distanceLabel = "2.1km",
            priceNumberLabel = "1,746",
            priceUnitLabel = "원",
            distanceNumberLabel = "2.1",
            distanceUnitLabel = "km",
            priceHistory = StationListPriceHistoryUiModel.Increased(20),
            isWatched = false,
            latitude = 37.503123,
            longitude = 127.039540,
        ),
        StationListItemUiModel(
            id = "station-4",
            name = "GS칼텍스 대치점",
            brand = Brand.GSC,
            brandLabel = "GS칼텍스",
            priceWon = 1_758,
            priceLabel = "1,758원",
            distanceLabel = "2.7km",
            priceNumberLabel = "1,758",
            priceUnitLabel = "원",
            distanceNumberLabel = "2.7",
            distanceUnitLabel = "km",
            priceHistory = StationListPriceHistoryUiModel.Decreased(30),
            isWatched = false,
            latitude = 37.506123,
            longitude = 127.043540,
        ),
    )

    private val populatedState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        preferences = UserPreferences.default(),
        stations = cachedStations,
        currentAddressLabel = "서울특별시 강남구 역삼동",
    )

    private val emptyState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = false,
        preferences = UserPreferences.default(),
        stations = emptyList(),
        blockingFailure = null,
    )

    private val loadingWithCacheState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = true,
        preferences = UserPreferences.default(),
        stations = cachedStations,
        blockingFailure = null,
        currentAddressLabel = "서울특별시 강남구 역삼동",
    )

    private val staleState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = false,
        isStale = true,
        preferences = UserPreferences.default(),
        stations = cachedStations,
        blockingFailure = null,
        currentAddressLabel = "서울특별시 강남구 역삼동",
        lastUpdatedAt = java.time.Instant.parse("2026-05-11T00:30:00Z"),
    )

    private val permissionState = StationListUiState(
        permissionState = LocationPermissionState.Denied,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
    )

    private val gpsState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = false,
    )

    private val failureState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = false,
        preferences = UserPreferences.default(),
        stations = emptyList(),
        blockingFailure = StationListFailureReason.RefreshFailed,
    )

    @Test
    fun populated_state() {
        renderAndCapture("populated.png", populatedState)
    }

    @Test
    fun populated_dark_state() {
        renderAndCapture("populated-dark.png", populatedState, darkTheme = true)
    }

    @Test
    fun radius_menu_open_state() {
        renderOpenMenuAndCapture(
            name = "radius-menu-open.png",
            filterTag = STATION_LIST_RADIUS_FILTER_TAG,
        )
    }

    @Test
    fun fuel_menu_open_state() {
        renderOpenMenuAndCapture(
            name = "fuel-menu-open.png",
            filterTag = STATION_LIST_FUEL_FILTER_TAG,
        )
    }

    @Test
    fun brand_menu_open_state() {
        setPopulatedContent()
        composeRule.onNodeWithTag(STATION_LIST_BRAND_FILTER_TAG).performClick()
        settleAnimations()
        composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG).assertIsDisplayed()

        val alteulTag = "$STATION_LIST_FILTER_OPTION_TAG_PREFIX${BrandFilter.ALTEUL.name}"
        val etcTag = "$STATION_LIST_FILTER_OPTION_TAG_PREFIX${BrandFilter.ETC.name}"
        composeRule.onNodeWithTag(etcTag).performScrollTo()
        settleAnimations()
        composeRule.onNodeWithTag(alteulTag).assertIsDisplayed()
        composeRule.onNodeWithTag(etcTag).assertIsDisplayed()

        captureAndAssertTopBar("brand-menu-open.png")
    }

    @Test
    fun empty_state() {
        renderAndCapture("empty.png", emptyState)
    }

    @Test
    fun loading_with_cache_state() {
        renderAndCapture("loading-with-cache.png", loadingWithCacheState)
    }

    @Test
    fun stale_state() {
        renderAndCapture("stale.png", staleState)
    }

    @Test
    fun permission_state() {
        renderAndCapture("permission.png", permissionState)
    }

    @Test
    fun gps_state() {
        renderAndCapture("gps.png", gpsState)
    }

    @Test
    fun failure_state() {
        renderAndCapture("failure.png", failureState)
    }

    private fun renderAndCapture(name: String, uiState: StationListUiState, darkTheme: Boolean = false) {
        setContent(uiState = uiState, darkTheme = darkTheme)
        captureAndAssertTopBar(name)
    }

    private fun renderOpenMenuAndCapture(name: String, filterTag: String) {
        setPopulatedContent()
        composeRule.onNodeWithTag(filterTag).performClick()
        settleAnimations()
        composeRule.onNodeWithTag(STATION_LIST_FILTER_MENU_TAG).assertIsDisplayed()
        captureAndAssertTopBar(name)
    }

    private fun setPopulatedContent() {
        setContent(uiState = populatedState)
    }

    private fun setContent(uiState: StationListUiState, darkTheme: Boolean = false) {
        val snackbarHostState = SnackbarHostState()
        // Robolectric's native canvas can retain pixels between screenshot tests. An opaque,
        // conspicuous staging frame forces a full redraw and makes any incomplete redraw visible.
        val showContent = mutableStateOf(false)
        composeRule.setContent {
            if (showContent.value) {
                GasStationTheme(darkTheme = darkTheme) {
                    StationListScreen(
                        uiState = uiState,
                        snackbarHostState = snackbarHostState,
                        onAction = {},
                        onRequestPermissions = {},
                        onOpenLocationSettings = {},
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Magenta))
            }
        }
        composeRule.runOnIdle { showContent.value = true }
        settleAnimations()
    }

    private fun settleAnimations() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun captureAndAssertTopBar(name: String) {
        val snapshotPath = "src/test/snapshots/$name"
        composeRule.onRoot().captureRoboImage(snapshotPath)
        assertTopBarChromeCaptured(snapshotPath)
    }

    private fun assertTopBarChromeCaptured(snapshotPath: String) {
        val bitmap = requireNotNull(
            BitmapFactory.decodeFile(snapshotPath, BitmapFactory.Options().apply { inScaled = false }),
        ) {
            "Unable to read recorded snapshot: $snapshotPath"
        }
        val topBarBottom = bitmap.height * 8 / 100
        val titleYellowPixelCount = (0 until bitmap.width * 2 / 3).sumOf { x ->
            (0 until topBarBottom).count { y ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.alpha(pixel) > 200 &&
                    android.graphics.Color.red(pixel) > 200 &&
                    android.graphics.Color.green(pixel) > 170 &&
                    android.graphics.Color.blue(pixel) < 80
            }
        }
        val actionYellowPixelCount = (bitmap.width * 2 / 3 until bitmap.width).sumOf { x ->
            (0 until topBarBottom).count { y ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.alpha(pixel) > 200 &&
                    android.graphics.Color.red(pixel) > 200 &&
                    android.graphics.Color.green(pixel) > 170 &&
                    android.graphics.Color.blue(pixel) < 80
            }
        }
        assertTrue(
            "Top-bar title and refresh action were not captured in $snapshotPath " +
                "(${bitmap.width}x${bitmap.height}, title=$titleYellowPixelCount, action=$actionYellowPixelCount)",
            titleYellowPixelCount > 100 && actionYellowPixelCount > 100,
        )
    }
}
