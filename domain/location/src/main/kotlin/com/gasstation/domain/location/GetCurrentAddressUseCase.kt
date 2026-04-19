package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import javax.inject.Inject

class GetCurrentAddressUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(
        coordinates: Coordinates,
    ): LocationAddressLookupResult = repository.getCurrentAddress(coordinates)
}
