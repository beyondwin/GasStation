package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.MapProvider

data class StationListUiCommand(val id: Long, val payload: StationListCommandPayload)

sealed interface StationListCommandPayload {
    data class OpenExternalMap(
        val provider: MapProvider,
        val stationName: String,
        val originLatitude: Double?,
        val originLongitude: Double?,
        val latitude: Double,
        val longitude: Double,
    ) : StationListCommandPayload

    data object OpenLocationSettings : StationListCommandPayload

    data class ShowSnackbar(val message: StringResource) : StationListCommandPayload
}
