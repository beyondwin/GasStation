package com.gasstation.feature.stationlist

sealed interface StationListFailureReason {
    data object LocationTimedOut : StationListFailureReason

    data object LocationFailed : StationListFailureReason

    data object RefreshTimedOut : StationListFailureReason

    data object RefreshFailed : StationListFailureReason
}
