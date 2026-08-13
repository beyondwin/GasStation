package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState

internal sealed interface StationListBodyState {
    data object PermissionRequired : StationListBodyState

    data object GpsRequired : StationListBodyState

    data object InitialLoading : StationListBodyState

    data class Failure(val reason: StationListFailureReason) : StationListBodyState

    data object Results : StationListBodyState
}

internal fun StationListUiState.toBodyState(): StationListBodyState {
    val hasRenderableSnapshot = hasCachedSnapshot || stations.isNotEmpty()
    return when {
        permissionState == LocationPermissionState.Denied -> StationListBodyState.PermissionRequired
        !isGpsEnabled -> StationListBodyState.GpsRequired
        preferenceLoadFailed -> StationListBodyState.Failure(StationListFailureReason.PreferencesFailed)
        preferences == null -> StationListBodyState.InitialLoading
        blockingFailure != null && !hasRenderableSnapshot -> StationListBodyState.Failure(blockingFailure)
        !hasRenderableSnapshot -> StationListBodyState.InitialLoading
        else -> StationListBodyState.Results
    }
}
