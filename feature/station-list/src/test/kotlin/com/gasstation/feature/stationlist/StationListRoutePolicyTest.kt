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
}
