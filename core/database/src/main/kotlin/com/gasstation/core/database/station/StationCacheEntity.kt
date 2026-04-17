package com.gasstation.core.database.station

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "station_cache",
    primaryKeys = [
        "latitudeBucket",
        "longitudeBucket",
        "radiusMeters",
        "fuelType",
        "stationId",
    ],
    indices = [
        Index(value = ["fetchedAtEpochMillis"]),
    ],
)
data class StationCacheEntity(
    val latitudeBucket: Int,
    val longitudeBucket: Int,
    val radiusMeters: Int,
    val fuelType: String,
    val stationId: String,
    val brandCode: String,
    val name: String,
    val priceWon: Int,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMillis: Long,
)
