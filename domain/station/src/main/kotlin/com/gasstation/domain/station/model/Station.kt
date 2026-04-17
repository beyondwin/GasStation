package com.gasstation.domain.station.model

import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon

data class Station(
    val id: String,
    val name: String,
    val brand: Brand,
    val price: MoneyWon,
    val distance: DistanceMeters,
    val coordinates: Coordinates,
)
