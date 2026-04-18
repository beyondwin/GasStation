package com.gasstation.core.database.station

import androidx.room.Entity

@Entity(
    tableName = "station_cache_snapshot",
    primaryKeys = [
        "latitudeBucket",
        "longitudeBucket",
        "radiusMeters",
        "fuelType",
    ],
)
data class StationCacheSnapshotEntity(
    val latitudeBucket: Int,
    val longitudeBucket: Int,
    val radiusMeters: Int,
    val fuelType: String,
    val fetchedAtEpochMillis: Long,
)
