package com.gasstation.domain.station.model

sealed interface StationFreshness {
    data object Fresh : StationFreshness

    data object Stale : StationFreshness
}
