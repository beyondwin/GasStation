package com.gasstation.domain.settings

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPreferencesTest {

    @Test
    fun `defaults match current legacy behavior`() {
        val defaults = UserPreferences.default()

        assertEquals(SearchRadius.KM_3, defaults.searchRadius)
        assertEquals(FuelType.GASOLINE, defaults.fuelType)
        assertEquals(BrandFilter.ALL, defaults.brandFilter)
        assertEquals(SortOrder.DISTANCE, defaults.sortOrder)
        assertEquals(MapProvider.TMAP, defaults.mapProvider)
    }
}
