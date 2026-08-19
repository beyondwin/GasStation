package com.gasstation.domain.station

public class StationRefreshException(public val reason: StationRefreshFailureReason, cause: Throwable? = null) :
    IllegalStateException("Failed to refresh nearby stations: $reason", cause)
