package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState

internal fun StationListUiState.shouldAutoRefreshOnRoute(): Boolean = preferences != null &&
    permissionState != LocationPermissionState.Denied &&
    isAvailabilityKnown &&
    isGpsEnabled &&
    (currentCoordinates == null || needsRecoveryRefresh)

internal fun StationListUiState.watchlistCoordinatesOrNull(): Coordinates? = currentCoordinates?.takeIf {
    permissionState != LocationPermissionState.Denied && isGpsEnabled
}
