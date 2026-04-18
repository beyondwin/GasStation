package com.gasstation.domain.location

import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeAvailability(): Flow<Boolean>

    suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult
}
