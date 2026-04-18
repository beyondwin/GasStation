package com.gasstation.domain.station.model

sealed interface StationEvent {
    data class SearchRefreshed(
        val radius: SearchRadius,
        val fuelType: FuelType,
        val sortOrder: SortOrder,
        val stale: Boolean,
    ) : StationEvent

    data class WatchToggled(
        val stationId: String,
        val watched: Boolean,
    ) : StationEvent

    data class CompareViewed(val count: Int) : StationEvent

    data class ExternalMapOpened(
        val stationId: String,
        val provider: MapProvider,
    ) : StationEvent
}
