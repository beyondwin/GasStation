package com.gasstation.core.database.station

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "station_price_history",
    primaryKeys = ["stationId", "fuelType", "fetchedAtEpochMillis"],
    indices = [
        Index(value = ["stationId", "fuelType"]),
        Index(value = ["fetchedAtEpochMillis"]),
    ],
)
data class StationPriceHistoryEntity(
    val stationId: String,
    val fuelType: String,
    val priceWon: Int,
    val fetchedAtEpochMillis: Long,
)
