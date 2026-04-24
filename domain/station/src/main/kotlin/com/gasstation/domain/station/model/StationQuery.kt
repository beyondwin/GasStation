package com.gasstation.domain.station.model

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder

data class StationQuery(
    val coordinates: Coordinates,
    val radius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
) {
    fun toCacheKey(bucketMeters: Int): StationQueryCacheKey {
        require(bucketMeters > 0) { "bucketMeters must be greater than 0" }

        val latitudeBucket = ((coordinates.latitude * 111_000) / bucketMeters).toInt()
        val longitudeBucket = ((coordinates.longitude * 88_800) / bucketMeters).toInt()

        return StationQueryCacheKey(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radius.meters,
            fuelType = fuelType,
        )
    }
}
