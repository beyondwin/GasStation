package com.gasstation.core.location

interface ForegroundLocationProvider {
    suspend fun currentLocation(permissionState: LocationPermissionState): LocationLookupResult
}
