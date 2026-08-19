package com.gasstation.domain.location

import com.gasstation.core.model.Coordinates
import javax.inject.Inject

public class GetCurrentAddressUseCase @Inject public constructor(private val repository: LocationRepository) {
    public suspend operator fun invoke(coordinates: Coordinates): LocationAddressLookupResult = repository.getCurrentAddress(coordinates)
}
