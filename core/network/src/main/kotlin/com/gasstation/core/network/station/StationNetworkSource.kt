package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

interface StationNetworkSource {
    suspend fun fetchStations(origin: Coordinates, radius: SearchRadius, fuelType: FuelType): NetworkStationFetchResult
}
