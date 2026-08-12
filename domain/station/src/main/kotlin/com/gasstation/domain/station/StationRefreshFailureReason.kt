package com.gasstation.domain.station

sealed interface StationRefreshFailureReason {
    data object Timeout : StationRefreshFailureReason

    data object Network : StationRefreshFailureReason

    data object InvalidPayload : StationRefreshFailureReason

    data class Http(val statusCode: Int) : StationRefreshFailureReason

    data object Unknown : StationRefreshFailureReason
}
