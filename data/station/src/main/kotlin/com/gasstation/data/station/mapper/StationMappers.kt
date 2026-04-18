package com.gasstation.data.station.mapper

import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.network.model.OpinetStationDto
import com.gasstation.core.network.station.LocalKoreanCoordinateTransform
import com.gasstation.data.station.RemoteStation
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQueryCacheKey
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal fun OpinetStationDto.toRemoteStation(): RemoteStation? {
    val stationId = stationId?.takeIf { it.isNotBlank() } ?: return null
    val name = name?.takeIf { it.isNotBlank() } ?: return null
    val brandCode = brandCode?.takeIf { it.isNotBlank() } ?: return null
    val priceWon = priceWon?.toIntOrNull() ?: return null
    val rawX = gisX?.toDoubleOrNull() ?: return null
    val rawY = gisY?.toDoubleOrNull() ?: return null

    return RemoteStation(
        stationId = stationId,
        name = name,
        brandCode = brandCode,
        priceWon = priceWon,
        coordinates = rawCoordinatesToWgs84(
            rawX = rawX,
            rawY = rawY,
        ) ?: return null,
    )
}

internal fun rawCoordinatesToWgs84(
    rawX: Double,
    rawY: Double,
): Coordinates? {
    if (rawY in -90.0..90.0 && rawX in -180.0..180.0) {
        return Coordinates(latitude = rawY, longitude = rawX)
    }

    return LocalKoreanCoordinateTransform.ktmToWgs84(x = rawX, y = rawY)
}

internal fun RemoteStation.toEntity(
    cacheKey: StationQueryCacheKey,
    fetchedAt: Instant,
): StationCacheEntity = StationCacheEntity(
    latitudeBucket = cacheKey.latitudeBucket,
    longitudeBucket = cacheKey.longitudeBucket,
    radiusMeters = cacheKey.radiusMeters,
    fuelType = cacheKey.fuelType.name,
    stationId = stationId,
    brandCode = brandCode,
    name = name,
    priceWon = priceWon,
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

internal fun StationCacheEntity.toDomainStation(
    queryCoordinates: Coordinates,
): Station = Station(
    id = stationId,
    name = name,
    brand = brandCode.toBrand(),
    price = MoneyWon(priceWon),
    distance = DistanceMeters(distanceBetween(queryCoordinates, Coordinates(latitude, longitude))),
    coordinates = Coordinates(latitude = latitude, longitude = longitude),
)

internal fun FuelType.toFuelProductCode(): String = when (this) {
    FuelType.GASOLINE -> "B027"
    FuelType.DIESEL -> "D047"
    FuelType.PREMIUM_GASOLINE -> "B034"
    FuelType.KEROSENE -> "C004"
    FuelType.LPG -> "K015"
}

private fun String.toBrand(): Brand = Brand.entries.firstOrNull { it.name == this } ?: Brand.ETC

private fun distanceBetween(
    origin: Coordinates,
    destination: Coordinates,
): Int {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(destination.latitude - origin.latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - origin.longitude)
    val originLatitudeRadians = Math.toRadians(origin.latitude)
    val destinationLatitudeRadians = Math.toRadians(destination.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitudeRadians) *
        cos(destinationLatitudeRadians) *
        sin(longitudeDelta / 2).let { it * it }
    val centralAngle = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return (earthRadiusMeters * centralAngle).roundToInt()
}
