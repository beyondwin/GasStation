package com.gasstation.feature.stationlist

internal fun StationListUiState.hasFirstUsableContent(): Boolean = when (toBodyState()) {
    StationListBodyState.PermissionRequired,
    StationListBodyState.GpsRequired,
    StationListBodyState.InitialLoading,
    -> false

    is StationListBodyState.Failure -> true

    StationListBodyState.Results -> stations.isNotEmpty() || (!isLoading && !isRefreshing)
}
