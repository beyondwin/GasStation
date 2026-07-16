package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

internal object DemoPortfolioStations {
    fun forQuery(radius: SearchRadius, fuelType: FuelType): List<DemoSeedRemoteStation> {
        if (radius != SearchRadius.KM_3 || fuelType != FuelType.GASOLINE) return emptyList()

        return listOf(
            DemoSeedRemoteStation(
                stationId = "DEMO-RTO-001",
                name = "행복드림 알뜰주유소",
                brandCode = "RTO",
                priceWon = 1_968,
                coordinates = Coordinates(latitude = 37.4935, longitude = 127.0258),
            ),
            DemoSeedRemoteStation(
                stationId = "DEMO-ETC-001",
                name = "우리동네 주유소",
                brandCode = "ETC",
                priceWon = 1_987,
                coordinates = Coordinates(latitude = 37.5004, longitude = 127.0321),
            ),
        )
    }
}
