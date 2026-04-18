package com.gasstation.domain.station

sealed interface StationRefreshFailureReason {
    data object Timeout : StationRefreshFailureReason

    data object Network : StationRefreshFailureReason

    data object InvalidPayload : StationRefreshFailureReason

    data object Unknown : StationRefreshFailureReason
}
