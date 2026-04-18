package com.gasstation.core.database.station

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_station")
data class WatchedStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val brandCode: String,
    val latitude: Double,
    val longitude: Double,
    val watchedAtEpochMillis: Long,
)
