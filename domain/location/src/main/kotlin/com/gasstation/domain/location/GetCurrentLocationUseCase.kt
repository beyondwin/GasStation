package com.gasstation.domain.location

import javax.inject.Inject

class GetCurrentLocationUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(
        permissionState: LocationPermissionState,
    ): LocationLookupResult = repository.getCurrentLocation(permissionState)
}
