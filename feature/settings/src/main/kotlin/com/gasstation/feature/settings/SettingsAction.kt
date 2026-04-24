package com.gasstation.feature.settings

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder

sealed interface SettingsAction {
    data class SortOrderSelected(val sortOrder: SortOrder) : SettingsAction
    data class FuelTypeSelected(val fuelType: FuelType) : SettingsAction
    data class SearchRadiusSelected(val radius: SearchRadius) : SettingsAction
    data class BrandFilterSelected(val brandFilter: BrandFilter) : SettingsAction
    data class MapProviderSelected(val mapProvider: MapProvider) : SettingsAction
}
