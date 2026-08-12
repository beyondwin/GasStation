package com.gasstation.data.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.station.NetworkStationFailure
import com.gasstation.core.network.station.NetworkStationFetchResult
import com.gasstation.core.network.station.StationNetworkSource
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject

interface StationRemoteDataSource {
    suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult
}

data class RemoteStation(val stationId: String, val name: String, val brandCode: String, val priceWon: Int, val coordinates: Coordinates)

sealed interface RemoteStationFetchResult {
    data class Success(val stations: List<RemoteStation>) : RemoteStationFetchResult

    data class Failure(val reason: StationRefreshFailureReason, val cause: Throwable? = null) : RemoteStationFetchResult
}

class DefaultStationRemoteDataSource @Inject constructor(private val stationNetworkSource: StationNetworkSource) :
    StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = when (
        val result = stationNetworkSource.fetchStations(
            origin = query.coordinates,
            radius = query.radius,
            fuelType = query.fuelType,
        )
    ) {
        is NetworkStationFetchResult.Success -> RemoteStationFetchResult.Success(
            result.stations.map { station ->
                RemoteStation(
                    stationId = station.stationId,
                    name = station.name,
                    brandCode = station.brandCode,
                    priceWon = station.priceWon,
                    coordinates = station.coordinates,
                )
            },
        )

        is NetworkStationFetchResult.Failure -> RemoteStationFetchResult.Failure(
            reason = result.reason.toDomainFailureReason(),
            cause = result.cause,
        )
    }
}

private fun NetworkStationFailure.toDomainFailureReason(): StationRefreshFailureReason = when (this) {
    NetworkStationFailure.InvalidPayload -> StationRefreshFailureReason.InvalidPayload
    NetworkStationFailure.Timeout -> StationRefreshFailureReason.Timeout
    NetworkStationFailure.Network -> StationRefreshFailureReason.Network
    is NetworkStationFailure.Http -> StationRefreshFailureReason.Http(statusCode)
    NetworkStationFailure.Unknown -> StationRefreshFailureReason.Unknown
}
