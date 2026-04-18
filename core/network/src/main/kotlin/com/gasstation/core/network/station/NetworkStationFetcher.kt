package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.service.OpinetService
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius

class NetworkStationFetcher(
    private val opinetService: OpinetService,
    private val opinetApiKey: String,
) {
    suspend fun fetchStations(
        origin: Coordinates,
        radius: SearchRadius,
        fuelType: FuelType,
    ): NetworkStationFetchResult {
        val originInKtm = LocalKoreanCoordinateTransform.wgs84ToKtm(
            latitude = origin.latitude,
            longitude = origin.longitude,
        )

        val response = opinetService.findStations(
            code = opinetApiKey,
            x = originInKtm.x,
            y = originInKtm.y,
            radius = radius.meters,
            sort = OPINET_DISTANCE_SORT,
            fuelType = fuelType.toFuelProductCode(),
        )

        val rawStations = response.result?.stations ?: return NetworkStationFetchResult.Failure
        val normalizedStations = buildList {
            for (station in rawStations) {
                val mapped = station.toNetworkRemoteStation() ?: continue
                add(mapped)
            }
        }

        return when {
            normalizedStations.isNotEmpty() -> NetworkStationFetchResult.Success(normalizedStations)
            rawStations.isEmpty() -> NetworkStationFetchResult.Success(emptyList())
            else -> NetworkStationFetchResult.Failure
        }
    }
}

private const val OPINET_DISTANCE_SORT = "2"
