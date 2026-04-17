package com.gasstation.domain.station

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class StationQueryCacheKeyTest {

    @Test
    fun `cache key ignores sort order map provider and brand filter`() {
        val first = StationQuery(
            coordinates = Coordinates(37.498095, 127.027610),
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.DISTANCE,
            mapProvider = MapProvider.TMAP,
        )

        val second = first.copy(
            brandFilter = BrandFilter.SKE,
            sortOrder = SortOrder.PRICE,
            mapProvider = MapProvider.KAKAO_NAVI,
        )

        assertEquals(first.toCacheKey(bucketMeters = 250), second.toCacheKey(bucketMeters = 250))
    }
}
