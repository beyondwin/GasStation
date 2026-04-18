package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates

data class NetworkRemoteStation(
    val stationId: String,
    val name: String,
    val brandCode: String,
    val priceWon: Int,
    val coordinates: Coordinates,
)
