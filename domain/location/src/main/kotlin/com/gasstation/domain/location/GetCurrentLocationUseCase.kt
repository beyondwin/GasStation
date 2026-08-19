package com.gasstation.domain.location

import javax.inject.Inject

public class GetCurrentLocationUseCase @Inject public constructor(private val repository: LocationRepository) {
    public suspend operator fun invoke(permissionState: LocationPermissionState): LocationLookupResult =
        repository.getCurrentLocation(permissionState)
}
