package com.gasstation.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity

@Database(
    entities = [
        StationCacheEntity::class,
        StationPriceHistoryEntity::class,
        WatchedStationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class GasStationDatabase : RoomDatabase() {
    abstract fun stationCacheDao(): StationCacheDao
    abstract fun stationPriceHistoryDao(): StationPriceHistoryDao
    abstract fun watchedStationDao(): WatchedStationDao

    companion object {
        const val DATABASE_NAME: String = "gasstation.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `station_price_history` (
                        `stationId` TEXT NOT NULL,
                        `priceWon` INTEGER NOT NULL,
                        `fetchedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`, `fetchedAtEpochMillis`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_station_price_history_stationId`
                    ON `station_price_history` (`stationId`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_station_price_history_fetchedAtEpochMillis`
                    ON `station_price_history` (`fetchedAtEpochMillis`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watched_station` (
                        `stationId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `brandCode` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `watchedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
