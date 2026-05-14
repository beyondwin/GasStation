package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState

internal fun StationListUiState.shouldAutoRefreshOnRoute(): Boolean = isAvailabilityKnown &&
    isGpsEnabled &&
    (
        currentCoordinates == null ||
            hasDeniedLocationAccess ||
            needsRecoveryRefresh
        )

internal fun StationListUiState.watchlistCoordinatesOrNull(): Coordinates? = currentCoordinates?.takeIf {
    isGpsEnabled &&
        (
            permissionState != LocationPermissionState.Denied ||
                hasDeniedLocationAccess
            )
}
