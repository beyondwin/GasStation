package com.gasstation.core.network.station

sealed interface NetworkStationFailure {
    data object InvalidPayload : NetworkStationFailure

    data object Timeout : NetworkStationFailure

    data object Network : NetworkStationFailure

    data class Http(val statusCode: Int) : NetworkStationFailure

    data object Unknown : NetworkStationFailure
}
