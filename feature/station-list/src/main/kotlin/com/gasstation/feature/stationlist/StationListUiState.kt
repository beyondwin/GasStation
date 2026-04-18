package com.gasstation.feature.stationlist

import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import java.time.Instant

data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val isGpsEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val stations: List<StationListItemUiModel> = emptyList(),
    val selectedBrandFilter: BrandFilter = BrandFilter.ALL,
    val selectedRadius: SearchRadius = SearchRadius.KM_3,
    val selectedFuelType: FuelType = FuelType.GASOLINE,
    val selectedSortOrder: SortOrder = SortOrder.DISTANCE,
    val lastUpdatedAt: Instant? = null,
)
