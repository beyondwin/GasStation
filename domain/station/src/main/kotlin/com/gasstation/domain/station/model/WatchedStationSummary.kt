package com.gasstation.domain.station.model

import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import java.time.Instant

public data class WatchedStationSummary(
    val id: String,
    val name: String,
    val brand: Brand,
    val price: MoneyWon?,
    val distance: DistanceMeters,
    val coordinates: Coordinates,
    val priceDelta: StationPriceDelta,
    val lastSeenAt: Instant?,
)
