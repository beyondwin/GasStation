package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.domain.station.StationRefreshFailureReason

internal fun StationRefreshFailureReason?.toStationListRefreshFailureCopy(): StringResource = when (this) {
    StationRefreshFailureReason.Timeout -> StringResource.fromId(R.string.station_list_refresh_timeout)

    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    is StationRefreshFailureReason.Http,
    StationRefreshFailureReason.Unknown,
    null,
    -> StringResource.fromId(R.string.station_list_refresh_failed)
}
