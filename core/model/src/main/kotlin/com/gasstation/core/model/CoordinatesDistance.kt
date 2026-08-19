package com.gasstation.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

public fun Coordinates.distanceTo(destination: Coordinates): DistanceMeters {
    val latitudeDelta = Math.toRadians(destination.latitude - latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - longitude)
    val originLatitudeRadians = Math.toRadians(latitude)
    val destinationLatitudeRadians = Math.toRadians(destination.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitudeRadians) *
        cos(destinationLatitudeRadians) *
        sin(longitudeDelta / 2).let { it * it }
    val clampedHaversine = haversine.coerceIn(0.0, 1.0)
    val complement = (1.0 - clampedHaversine).coerceAtLeast(0.0)
    val centralAngle = 2 * atan2(sqrt(clampedHaversine), sqrt(complement))
    return DistanceMeters((EARTH_RADIUS_METERS * centralAngle).roundToInt())
}
