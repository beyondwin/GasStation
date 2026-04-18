package com.gasstation.tools.demoseed

import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius

data class DemoSeedQuery(
    val radius: SearchRadius,
    val fuelType: FuelType,
)

object DemoSeedQueryMatrix {
    fun all(): List<DemoSeedQuery> = SearchRadius.entries.flatMap { radius ->
        FuelType.entries.map { fuelType ->
            DemoSeedQuery(
                radius = radius,
                fuelType = fuelType,
            )
        }
    }
}
