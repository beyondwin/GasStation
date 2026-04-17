package com.gasstation.feature.settings

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder

data class SettingsUiState(
    val searchRadius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
    val mapProvider: MapProvider,
) {
    companion object {
        fun from(preferences: UserPreferences) = SettingsUiState(
            searchRadius = preferences.searchRadius,
            fuelType = preferences.fuelType,
            brandFilter = preferences.brandFilter,
            sortOrder = preferences.sortOrder,
            mapProvider = preferences.mapProvider,
        )
    }
}
