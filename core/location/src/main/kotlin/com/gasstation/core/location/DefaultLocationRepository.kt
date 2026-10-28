package com.gasstation.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import com.gasstation.domain.location.LocationLookupResult
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.location.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Optional
import javax.inject.Inject

internal class DefaultLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundLocationProvider: ForegroundLocationProvider,
    private val addressResolver: AddressResolver,
    private val demoLocationOverride: Optional<DemoLocationOverride>,
) : LocationRepository {
    override fun observeAvailability(): Flow<Boolean> = if (demoLocationOverride.isPresent) {
        flowOf(true)
    } else {
        context.locationAvailabilityFlow()
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult =
        foregroundLocationProvider.currentLocation(permissionState)

    override suspend fun getCurrentAddress(coordinates: Coordinates): LocationAddressLookupResult = addressResolver.addressFor(coordinates)
}
