package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState

internal sealed interface StationListBodyState {
    data object PermissionRequired : StationListBodyState

    data object GpsRequired : StationListBodyState

    data object InitialLoading : StationListBodyState

    data class Failure(val reason: StationListFailureReason) : StationListBodyState

    data object Results : StationListBodyState
}

internal fun StationListUiState.toBodyState(): StationListBodyState = when {
    permissionState == LocationPermissionState.Denied -> StationListBodyState.PermissionRequired
    !isGpsEnabled -> StationListBodyState.GpsRequired
    preferenceLoadFailed -> StationListBodyState.Failure(StationListFailureReason.PreferencesFailed)
    preferences == null -> StationListBodyState.InitialLoading
    isLoading && stations.isEmpty() -> StationListBodyState.InitialLoading
    blockingFailure != null && stations.isEmpty() -> StationListBodyState.Failure(blockingFailure)
    else -> StationListBodyState.Results
}
