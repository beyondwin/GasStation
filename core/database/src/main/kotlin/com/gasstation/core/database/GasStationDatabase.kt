package com.gasstation.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity

@Database(
    entities = [StationCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GasStationDatabase : RoomDatabase() {
    abstract fun stationCacheDao(): StationCacheDao

    companion object {
        const val DATABASE_NAME: String = "gasstation.db"
    }
}
