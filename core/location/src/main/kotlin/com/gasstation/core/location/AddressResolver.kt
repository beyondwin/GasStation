package com.gasstation.core.location

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult

internal interface AddressResolver {
    suspend fun addressFor(coordinates: Coordinates): LocationAddressLookupResult
}
