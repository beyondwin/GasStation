package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.model.OpinetStationDto
import com.gasstation.core.model.FuelType

internal fun OpinetStationDto.toNetworkRemoteStation(): NetworkRemoteStation? {
    val stationId = stationId?.takeIf(String::isNotBlank) ?: return null
    val name = name?.takeIf(String::isNotBlank) ?: return null
    val brandCode = brandCode?.takeIf(String::isNotBlank) ?: return null
    val priceWon = priceWon?.toIntOrNull() ?: return null
    val rawX = gisX?.toDoubleOrNull() ?: return null
    val rawY = gisY?.toDoubleOrNull() ?: return null

    return NetworkRemoteStation(
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

fun FuelType.toFuelProductCode(): String = when (this) {
    FuelType.GASOLINE -> "B027"
    FuelType.DIESEL -> "D047"
    FuelType.PREMIUM_GASOLINE -> "B034"
    FuelType.KEROSENE -> "C004"
    FuelType.LPG -> "K015"
}
