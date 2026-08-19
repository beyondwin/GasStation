package com.gasstation.domain.station.model

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType

public data class WatchlistQuery(val origin: Coordinates, val fuelType: FuelType)
