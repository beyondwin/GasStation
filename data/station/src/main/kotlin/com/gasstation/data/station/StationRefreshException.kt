package com.gasstation.data.station

class StationRefreshException(
    val reason: StationRefreshFailureReason,
    cause: Throwable? = null,
) : IllegalStateException("Failed to refresh nearby stations: $reason", cause)
