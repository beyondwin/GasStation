package com.gasstation.domain.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.Station
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrandFilterTest {

    @Test
    fun `all filter matches every station brand`() {
        val station = station(brand = Brand.GSC)

        assertTrue(BrandFilter.ALL.matches(station.brand))
    }

    @Test
    fun `specific filter matches only the same station brand`() {
        val station = station(brand = Brand.GSC)

        assertTrue(BrandFilter.GSC.matches(station.brand))
        assertFalse(BrandFilter.SKE.matches(station.brand))
    }

    private fun station(brand: Brand): Station =
        Station(
            id = "station-1",
            name = "Sample",
            brand = brand,
            price = MoneyWon(1_689),
            distance = DistanceMeters(800),
            coordinates = Coordinates(latitude = 37.498095, longitude = 127.027610),
        )
}
