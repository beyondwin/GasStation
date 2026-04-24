package com.gasstation.domain.settings

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPreferencesTest {

    @Test
    fun `defaults stay aligned with station domain value objects`() {
        val defaults = UserPreferences.default()

        assertEquals(SearchRadius.KM_3, defaults.searchRadius)
        assertEquals(FuelType.GASOLINE, defaults.fuelType)
        assertEquals(BrandFilter.ALL, defaults.brandFilter)
        assertEquals(SortOrder.DISTANCE, defaults.sortOrder)
        assertEquals(MapProvider.TMAP, defaults.mapProvider)
    }
}
