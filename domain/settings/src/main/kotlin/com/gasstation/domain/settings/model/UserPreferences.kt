package com.gasstation.domain.settings.model

import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder

data class UserPreferences(
    val searchRadius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
    val mapProvider: MapProvider,
) {
    companion object {
        fun default(): UserPreferences =
            UserPreferences(
                searchRadius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
                brandFilter = BrandFilter.ALL,
                sortOrder = SortOrder.DISTANCE,
                mapProvider = MapProvider.TMAP,
            )
    }
}
