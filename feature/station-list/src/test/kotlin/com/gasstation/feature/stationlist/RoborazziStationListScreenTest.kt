package com.gasstation.feature.stationlist

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.Brand
import com.gasstation.domain.location.LocationPermissionState
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    // Four UI states: empty, loading-with-cache, stale, error

    private val cachedStations = listOf(
        StationListItemUiModel(
            id = "station-1",
            name = "SK에너지 강남점",
            brand = Brand.SKE,
            brandLabel = "SK에너지",
            priceLabel = "1,712원",
            distanceLabel = "1.2km",
            priceNumberLabel = "1,712",
            priceUnitLabel = "원",
            distanceNumberLabel = "1.2",
            distanceUnitLabel = "km",
            priceDeltaLabel = "-",
            priceDeltaTone = PriceDeltaTone.Neutral,
            isWatched = false,
            latitude = 37.497942,
            longitude = 127.027621,
        ),
        StationListItemUiModel(
            id = "station-2",
            name = "GS칼텍스 역삼점",
            brand = Brand.GSC,
            brandLabel = "GS칼텍스",
            priceLabel = "1,738원",
            distanceLabel = "1.8km",
            priceNumberLabel = "1,738",
            priceUnitLabel = "원",
            distanceNumberLabel = "1.8",
            distanceUnitLabel = "km",
            priceDeltaLabel = "12원",
            priceDeltaTone = PriceDeltaTone.Rise,
            isWatched = true,
            latitude = 37.500123,
            longitude = 127.036540,
        ),
    )

    private val emptyState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = false,
        stations = emptyList(),
        blockingFailure = null,
    )

    private val loadingWithCacheState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = true,
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
        stations = cachedStations,
        blockingFailure = null,
        currentAddressLabel = "서울특별시 강남구 역삼동",
        lastUpdatedAt = java.time.Instant.parse("2026-05-11T00:30:00Z"),
    )

    private val errorState = StationListUiState(
        permissionState = LocationPermissionState.PreciseGranted,
        isAvailabilityKnown = true,
        isGpsEnabled = true,
        isLoading = false,
        stations = emptyList(),
        blockingFailure = StationListFailureReason.RefreshFailed,
    )

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
    fun error_state() {
        renderAndCapture("error.png", errorState)
    }

    private fun renderAndCapture(name: String, uiState: StationListUiState) {
        val snackbarHostState = SnackbarHostState()
        composeRule.setContent {
            GasStationTheme {
                StationListScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onAction = {},
                    onRequestPermissions = {},
                    onOpenLocationSettings = {},
                    onSettingsClick = {},
                    onWatchlistClick = null,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name")
    }
}
