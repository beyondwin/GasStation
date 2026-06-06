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

        val latitudeBucket = ((coordinates.latitude * METERS_PER_LATITUDE_DEGREE) / bucketMeters).toInt()
        val longitudeBucket = ((coordinates.longitude * METERS_PER_LONGITUDE_DEGREE_KR) / bucketMeters).toInt()

        return StationQueryCacheKey(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radius.meters,
            fuelType = fuelType,
        )
    }

    private companion object {
        // 위도 1도 ≈ 111km (전 지구 공통 근사).
        const val METERS_PER_LATITUDE_DEGREE = 111_000

        // 경도 1도당 미터는 위도에 따라 줄어든다. 이 값은 한국 위도(약 37도) 근사치이며,
        // 캐시 버킷팅 전용 좌표 양자화에만 쓰인다(정밀 거리 계산용 아님).
        const val METERS_PER_LONGITUDE_DEGREE_KR = 88_800
    }
}
