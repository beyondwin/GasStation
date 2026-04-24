package com.gasstation.domain.station.model

import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.StationRefreshFailureReason

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

    data class RefreshFailed(
        val reason: StationRefreshFailureReason,
    ) : StationEvent

    data class LocationFailed(
        val resultType: String,
    ) : StationEvent

    data class RetryAttempted(
        val originalReason: StationRefreshFailureReason,
        val succeeded: Boolean,
    ) : StationEvent
}
