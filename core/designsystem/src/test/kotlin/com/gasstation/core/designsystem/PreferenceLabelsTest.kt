package com.gasstation.core.designsystem

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceLabelsTest {
    @Test
    fun `radius and fuel values map to canonical shared resources`() {
        assertEquals(
            StringResource.fromId(R.string.gas_station_radius_km5),
            SearchRadius.KM_5.gasStationSearchRadiusLabel(),
        )
        assertEquals(
            StringResource.fromId(R.string.gas_station_fuel_diesel),
            FuelType.DIESEL.gasStationFuelTypeLabel(),
        )
    }
}
