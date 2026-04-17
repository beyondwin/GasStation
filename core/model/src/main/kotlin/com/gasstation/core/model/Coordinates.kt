package com.gasstation.core.model

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude must be between -90.0 and 90.0" }
        require(longitude in -180.0..180.0) { "longitude must be between -180.0 and 180.0" }
    }
}
