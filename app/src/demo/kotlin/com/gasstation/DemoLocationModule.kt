package com.gasstation

import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates

object DemoLocationModule {
    private val reviewerCoordinates = Coordinates(
        latitude = 37.498095,
        longitude = 127.02761,
    )

    fun currentLocation(permissionState: LocationPermissionState): Coordinates? =
        if (permissionState == LocationPermissionState.Denied) {
            null
        } else {
            reviewerCoordinates
        }
}
