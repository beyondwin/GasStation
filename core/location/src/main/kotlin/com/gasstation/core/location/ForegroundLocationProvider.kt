package com.gasstation.core.location

import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState

interface ForegroundLocationProvider {
    suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult
}
