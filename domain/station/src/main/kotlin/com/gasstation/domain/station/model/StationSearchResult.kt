package com.gasstation.domain.station.model

import java.time.Instant

data class StationSearchResult(
    val stations: List<Station>,
    val freshness: StationFreshness,
    val fetchedAt: Instant?,
)
