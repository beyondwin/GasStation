package com.gasstation.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.gasstation.domain.location.LocationLookupResult as DomainLocationLookupResult
import com.gasstation.domain.location.LocationPermissionState as DomainLocationPermissionState
import com.gasstation.domain.location.LocationRepository as DomainLocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundLocationProvider: ForegroundLocationProvider,
) : DomainLocationRepository {
    override fun observeAvailability(): Flow<Boolean> = context.locationAvailabilityFlow()

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(
        permissionState: DomainLocationPermissionState,
    ): DomainLocationLookupResult =
        when (val result = foregroundLocationProvider.currentLocation(permissionState.toCorePermissionState())) {
            is LocationLookupResult.Success -> DomainLocationLookupResult.Success(result.coordinates)
            LocationLookupResult.PermissionDenied -> DomainLocationLookupResult.PermissionDenied
            LocationLookupResult.Unavailable -> DomainLocationLookupResult.Unavailable
            LocationLookupResult.TimedOut -> DomainLocationLookupResult.TimedOut
            is LocationLookupResult.Error -> DomainLocationLookupResult.Error(result.throwable)
        }
}

private fun DomainLocationPermissionState.toCorePermissionState(): LocationPermissionState = when (this) {
    DomainLocationPermissionState.Denied -> LocationPermissionState.Denied
    DomainLocationPermissionState.ApproximateGranted -> LocationPermissionState.ApproximateGranted
    DomainLocationPermissionState.PreciseGranted -> LocationPermissionState.PreciseGranted
}
