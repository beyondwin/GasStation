package com.gasstation.feature.stationlist

import com.gasstation.core.model.MapProvider

sealed interface StationListEffect {
    data class OpenExternalMap(
        val provider: MapProvider,
        val stationName: String,
        val originLatitude: Double?,
        val originLongitude: Double?,
        val latitude: Double,
        val longitude: Double,
    ) : StationListEffect

    data object OpenLocationSettings : StationListEffect

    data class ShowSnackbar(val message: String) : StationListEffect
}
