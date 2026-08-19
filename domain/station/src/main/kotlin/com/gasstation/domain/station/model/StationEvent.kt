package com.gasstation.domain.station.model

import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.StationRefreshFailureReason

public sealed interface StationEvent {
    public data class SearchRefreshed(val radius: SearchRadius, val fuelType: FuelType, val sortOrder: SortOrder, val stale: Boolean) :
        StationEvent

    public data class WatchToggled(val stationId: String, val watched: Boolean) : StationEvent

    public data class CompareViewed(val count: Int) : StationEvent

    public data class ExternalMapOpened(val stationId: String, val provider: MapProvider) : StationEvent

    public data class RefreshFailed(val reason: StationRefreshFailureReason) : StationEvent

    public data class LocationFailed(val resultType: String) : StationEvent

    public data class RetryAttempted(val originalReason: StationRefreshFailureReason, val succeeded: Boolean) : StationEvent
}
