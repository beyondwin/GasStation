package com.gasstation.core.network.station

sealed interface NetworkStationFetchResult {
    data class Success(val stations: List<NetworkRemoteStation>) : NetworkStationFetchResult

    data class Failure(val reason: NetworkStationFailure, val cause: Throwable? = null) : NetworkStationFetchResult
}
