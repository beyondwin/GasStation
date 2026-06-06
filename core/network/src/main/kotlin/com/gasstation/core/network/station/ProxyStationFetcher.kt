package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.network.model.ProxyStationDto
import com.gasstation.core.network.model.ProxyStationSearchRequestDto
import com.gasstation.core.network.service.ProxyStationService

class ProxyStationFetcher(private val proxyStationService: ProxyStationService) : StationNetworkSource {
    override suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult {
        val response = proxyStationService.findStations(
            ProxyStationSearchRequestDto(
                latitude = origin.latitude,
                longitude = origin.longitude,
                radiusMeters = radius.meters,
                fuelType = fuelType.name,
            ),
        )
        val rawStations = response.stations
        val mappedStations = rawStations.mapNotNull(ProxyStationDto::toNetworkRemoteStation)

        return when {
            mappedStations.isNotEmpty() -> NetworkStationFetchResult.Success(mappedStations)
            rawStations.isEmpty() -> NetworkStationFetchResult.Success(emptyList())
            else -> NetworkStationFetchResult.Failure
        }
    }
}

private fun ProxyStationDto.toNetworkRemoteStation(): NetworkRemoteStation? {
    val id = stationId?.takeIf(String::isNotBlank) ?: return null
    val stationName = name?.takeIf(String::isNotBlank) ?: return null
    val brand = brandCode?.takeIf(String::isNotBlank) ?: return null
    val price = priceWon?.takeIf { it > 0 } ?: return null
    val lat = latitude ?: return null
    val lon = longitude ?: return null

    return NetworkRemoteStation(
        stationId = id,
        name = stationName,
        brandCode = brand,
        priceWon = price,
        coordinates = Coordinates.ofOrNull(latitude = lat, longitude = lon) ?: return null,
    )
}
