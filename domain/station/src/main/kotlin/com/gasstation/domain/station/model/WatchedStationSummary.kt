package com.gasstation.domain.station.model

import java.time.Instant

data class WatchedStationSummary(
    val station: Station,
    val priceDelta: StationPriceDelta,
    val lastSeenAt: Instant?,
)
