package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState

internal enum class PermissionAction {
    Request,
    OpenAppSettings,
}

internal fun permissionAction(deniedRequestCount: Int, shouldShowRationale: Boolean): PermissionAction =
    if (deniedRequestCount >= 2 && !shouldShowRationale) {
        PermissionAction.OpenAppSettings
    } else {
        PermissionAction.Request
    }

internal fun StationListUiState.shouldAutoRefreshOnRoute(): Boolean = preferences != null &&
    permissionState != LocationPermissionState.Denied &&
    isAvailabilityKnown &&
    isGpsEnabled &&
    (currentCoordinates == null || needsRecoveryRefresh)

internal fun StationListUiState.watchlistCoordinatesOrNull(): Coordinates? = currentCoordinates?.takeIf {
    permissionState != LocationPermissionState.Denied && isGpsEnabled
}
