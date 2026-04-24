package com.gasstation.analytics

import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.model.StationEvent
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LogcatStationEventLogger @Inject constructor() : StationEventLogger {
    override fun log(event: StationEvent) {
        Timber.tag(TAG).d(event.toLogMessage())
    }

    private fun StationEvent.toLogMessage(): String = when (this) {
        is StationEvent.SearchRefreshed -> {
            "search_refreshed radius=${radius.name} fuelType=${fuelType.name} sortOrder=${sortOrder.name} stale=$stale"
        }
        is StationEvent.WatchToggled -> {
            "watch_toggled stationId=$stationId watched=$watched"
        }
        is StationEvent.CompareViewed -> "compare_viewed count=$count"
        is StationEvent.ExternalMapOpened -> {
            "external_map_opened stationId=$stationId provider=${provider.name}"
        }
        is StationEvent.RefreshFailed -> {
            "refresh_failed reason=${reason::class.java.simpleName}"
        }
        is StationEvent.LocationFailed -> {
            "location_failed resultType=$resultType"
        }
        is StationEvent.RetryAttempted -> {
            "retry_attempted originalReason=${originalReason::class.java.simpleName} succeeded=$succeeded"
        }
    }

    private companion object {
        const val TAG = "StationEvent"
    }
}
