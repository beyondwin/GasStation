package com.gasstation.core.network.model

data class ProxyStationSearchRequestDto(val latitude: Double, val longitude: Double, val radiusMeters: Int, val fuelType: String)

data class ProxyStationSearchResponseDto(val stations: List<ProxyStationDto> = emptyList())

data class ProxyStationDto(
    val stationId: String? = null,
    val name: String? = null,
    val brandCode: String? = null,
    val fuelType: String? = null,
    val priceWon: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
