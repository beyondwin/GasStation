package com.gasstation.domain.station.model

import java.time.Instant

public data class StationListEntry(
    val station: Station,
    val priceDelta: StationPriceDelta,
    val isWatched: Boolean,
    val lastSeenAt: Instant?,
)
