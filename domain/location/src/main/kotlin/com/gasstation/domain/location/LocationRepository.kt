package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import kotlinx.coroutines.flow.Flow

public interface LocationRepository {
    public fun observeAvailability(): Flow<Boolean>

    public suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult

    public suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult
}
