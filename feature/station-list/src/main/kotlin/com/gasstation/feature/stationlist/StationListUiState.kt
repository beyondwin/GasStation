package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import java.time.Instant

data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    val currentAddressLabel: String? = null,
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val hasDeniedLocationAccess: Boolean = false,
    val needsRecoveryRefresh: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isAvailabilityKnown: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val blockingFailure: StationListFailureReason? = null,
    val stations: List<StationListItemUiModel> = emptyList(),
    val selectedBrandFilter: BrandFilter = BrandFilter.ALL,
    val selectedRadius: SearchRadius = SearchRadius.KM_3,
    val selectedFuelType: FuelType = FuelType.GASOLINE,
    val selectedSortOrder: SortOrder = SortOrder.DISTANCE,
    val lastUpdatedAt: Instant? = null,
)
