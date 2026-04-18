package com.gasstation.data.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.station.NetworkStationFetchResult
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject

interface StationRemoteDataSource {
    suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult
}

data class RemoteStation(
    val stationId: String,
    val name: String,
    val brandCode: String,
    val priceWon: Int,
    val coordinates: Coordinates,
)

sealed interface RemoteStationFetchResult {
    data class Success(
        val stations: List<RemoteStation>,
    ) : RemoteStationFetchResult

    data object Failure : RemoteStationFetchResult
}

class DefaultStationRemoteDataSource @Inject constructor(
    private val networkStationFetcher: NetworkStationFetcher,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        return try {
            when (
                val result = networkStationFetcher.fetchStations(
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
                NetworkStationFetchResult.Failure -> RemoteStationFetchResult.Failure
            }
        } catch (_: Exception) {
            RemoteStationFetchResult.Failure
        }
    }
}
