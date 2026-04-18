package com.gasstation.core.network.station

sealed interface NetworkStationFetchResult {
    data class Success(
        val stations: List<NetworkRemoteStation>,
    ) : NetworkStationFetchResult

    data object Failure : NetworkStationFetchResult
}
