package com.gasstation.core.model

public data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in LATITUDE_RANGE) { "latitude must be between -90.0 and 90.0" }
        require(longitude in LONGITUDE_RANGE) { "longitude must be between -180.0 and 180.0" }
    }

    public companion object {
        private val LATITUDE_RANGE = -90.0..90.0
        private val LONGITUDE_RANGE = -180.0..180.0

        /** 신뢰할 수 없는 외부 입력용. 범위를 벗어나면 예외 대신 null. */
        public fun ofOrNull(latitude: Double, longitude: Double): Coordinates? = if (latitude in LATITUDE_RANGE &&
            longitude in LONGITUDE_RANGE
        ) {
            Coordinates(latitude, longitude)
        } else {
            null
        }
    }
}
