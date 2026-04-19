package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeAvailability(): Flow<Boolean>

    suspend fun getCurrentLocation(
        permissionState: LocationPermissionState,
    ): LocationLookupResult

    suspend fun getCurrentAddress(
        coordinates: Coordinates,
    ): LocationAddressLookupResult
}
