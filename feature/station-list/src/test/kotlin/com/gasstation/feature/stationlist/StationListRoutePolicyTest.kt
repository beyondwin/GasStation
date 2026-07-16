package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationListRoutePolicyTest {
    private val coordinates = Coordinates(37.497927, 127.027583)

    @Test
    fun `auto refresh waits until availability is known`() {
        assertFalse(StationListUiState(isAvailabilityKnown = false).shouldAutoRefreshOnRoute())
    }

    @Test
    fun `auto refresh waits while gps is disabled`() {
        assertFalse(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = false,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh runs when no coordinates are available`() {
        assertTrue(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = null,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh runs for denied demo coordinates`() {
        assertTrue(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = coordinates,
                hasDeniedLocationAccess = true,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh runs for recovery refresh`() {
        assertTrue(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = coordinates,
                needsRecoveryRefresh = true,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `auto refresh is skipped for stable usable coordinates`() {
        assertFalse(
            StationListUiState(
                isAvailabilityKnown = true,
                isGpsEnabled = true,
                currentCoordinates = coordinates,
            ).shouldAutoRefreshOnRoute(),
        )
    }

    @Test
    fun `watchlist is hidden when denied permission has stale prod coordinates`() {
        assertNull(
            StationListUiState(
                currentCoordinates = coordinates,
                isGpsEnabled = true,
                permissionState = LocationPermissionState.Denied,
                hasDeniedLocationAccess = false,
            ).watchlistCoordinatesOrNull(),
        )
    }

    @Test
    fun `watchlist is visible for denied demo coordinates`() {
        assertEquals(
            coordinates,
            StationListUiState(
                currentCoordinates = coordinates,
                isGpsEnabled = true,
                permissionState = LocationPermissionState.Denied,
                hasDeniedLocationAccess = true,
            ).watchlistCoordinatesOrNull(),
        )
    }

    @Test
    fun `first content waits while permission is required`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.Denied,
                hasDeniedLocationAccess = false,
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content waits while gps is disabled`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isGpsEnabled = false,
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content waits during initial loading without cached stations`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready when a station card is visible`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = true,
                stations = listOf(testStationUiModel()),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready for a settled successful empty result`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isLoading = false,
                isRefreshing = false,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content waits for empty results while refresh is still active`() {
        assertFalse(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                isRefreshing = true,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready for blocking failure guidance`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.PreciseGranted,
                blockingFailure = StationListFailureReason.RefreshFailed,
                stations = emptyList(),
            ).hasFirstUsableContent(),
        )
    }

    @Test
    fun `first content is ready for stale cache with visible stations`() {
        assertTrue(
            StationListUiState(
                permissionState = LocationPermissionState.ApproximateGranted,
                isStale = true,
                isRefreshing = true,
                stations = listOf(testStationUiModel()),
            ).hasFirstUsableContent(),
        )
    }
}

private fun testStationUiModel() = StationListItemUiModel(
    id = "station-1",
    name = "테스트 주유소",
    brand = com.gasstation.core.model.Brand.GSC,
    brandLabel = "GS칼텍스",
    priceWon = 1_689,
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
)
