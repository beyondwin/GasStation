package com.gasstation.domain.station.model

public sealed interface StationFreshness {
    public data object Fresh : StationFreshness

    public data object Stale : StationFreshness
}
