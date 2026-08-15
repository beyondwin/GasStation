package com.gasstation.feature.stationlist

internal fun LocationAcquisitionResult.failureEventType(): String? = when (this) {
    is LocationAcquisitionResult.Success,
    LocationAcquisitionResult.Superseded,
    -> null

    LocationAcquisitionResult.PermissionDenied -> "PermissionDenied"

    LocationAcquisitionResult.TimedOut -> "TimedOut"

    LocationAcquisitionResult.Unavailable -> "Unavailable"

    is LocationAcquisitionResult.Error -> "Error"
}
