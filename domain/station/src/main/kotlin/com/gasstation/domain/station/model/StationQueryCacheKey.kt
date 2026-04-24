package com.gasstation.domain.station.model

import com.gasstation.core.model.FuelType

data class StationQueryCacheKey(
    val latitudeBucket: Int,
    val longitudeBucket: Int,
    val radiusMeters: Int,
    val fuelType: FuelType,
)
