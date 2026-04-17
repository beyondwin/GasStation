package com.gasstation.core.location

import com.gasstation.core.model.Coordinates

interface ForegroundLocationProvider {
    suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates?
}
