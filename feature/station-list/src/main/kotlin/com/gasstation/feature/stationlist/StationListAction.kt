package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState

sealed interface StationListAction {
    data object AutoRefreshRequested : StationListAction
    data object RefreshRequested : StationListAction
    data object RetryClicked : StationListAction
    data object SortToggleRequested : StationListAction
    data class WatchToggled(val stationId: String, val watched: Boolean) : StationListAction
    data class PermissionChanged(val permissionState: LocationPermissionState) : StationListAction
    data class GpsAvailabilityChanged(val isEnabled: Boolean) : StationListAction
    data class StationClicked(val station: StationListItemUiModel) : StationListAction
}
