package com.gasstation.core.location

import com.gasstation.core.model.Coordinates

fun interface DemoLocationOverride {
    fun currentLocation(): Coordinates?
}
