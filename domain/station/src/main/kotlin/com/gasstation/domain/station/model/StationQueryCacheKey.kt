package com.gasstation.domain.station.model

data class StationQueryCacheKey(
    val latitudeBucket: Int,
    val longitudeBucket: Int,
    val radiusMeters: Int,
    val fuelType: FuelType,
)
