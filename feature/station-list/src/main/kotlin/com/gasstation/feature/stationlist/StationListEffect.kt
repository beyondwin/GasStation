package com.gasstation.feature.stationlist

import com.gasstation.domain.station.model.MapProvider

sealed interface StationListEffect {
    data class OpenExternalMap(
        val provider: MapProvider,
        val stationName: String,
        val latitude: Double,
        val longitude: Double,
    ) : StationListEffect

    data object OpenLocationSettings : StationListEffect

    data class ShowSnackbar(val message: String) : StationListEffect
}
