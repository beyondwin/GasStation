package com.gasstation.domain.station

public sealed interface StationRefreshFailureReason {
    public data object Timeout : StationRefreshFailureReason

    public data object Network : StationRefreshFailureReason

    public data object InvalidPayload : StationRefreshFailureReason

    public data class Http(val statusCode: Int) : StationRefreshFailureReason

    public data object Unknown : StationRefreshFailureReason
}
