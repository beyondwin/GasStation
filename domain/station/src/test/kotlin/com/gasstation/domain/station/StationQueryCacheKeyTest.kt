package com.gasstation.domain.station

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StationQueryCacheKeyTest {

    private fun query() = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )

    @Test
    fun `cache key ignores sort order and brand filter`() {
        val first = query()

        val second = first.copy(
            brandFilter = BrandFilter.SKE,
            sortOrder = SortOrder.PRICE,
        )

        assertEquals(first.toCacheKey(bucketMeters = 250), second.toCacheKey(bucketMeters = 250))
    }

    @Test
    fun `cache key buckets coordinates by exact meter math`() {
        val key = query().toCacheKey(bucketMeters = 250)

        // latitudeBucket  = ((37.498095 * 111_000) / 250).toInt() = 16649
        // longitudeBucket = ((127.027610 * 88_800) / 250).toInt() = 45120
        assertEquals(16649, key.latitudeBucket)
        assertEquals(45120, key.longitudeBucket)
    }

    @Test
    fun `cache key rejects non-positive bucket meters`() {
        assertFailsWith<IllegalArgumentException> {
            query().toCacheKey(bucketMeters = 0)
        }
    }
}
