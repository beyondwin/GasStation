package com.gasstation.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationCacheSnapshotEntity
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity

@Database(
    entities = [
        StationCacheEntity::class,
        StationCacheSnapshotEntity::class,
        StationPriceHistoryEntity::class,
        WatchedStationEntity::class,
    ],
    version = 5,
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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `station_price_history`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `station_price_history` (
                        `stationId` TEXT NOT NULL,
                        `fuelType` TEXT NOT NULL,
                        `priceWon` INTEGER NOT NULL,
                        `fetchedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`, `fuelType`, `fetchedAtEpochMillis`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_station_price_history_stationId_fuelType`
                    ON `station_price_history` (`stationId`, `fuelType`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_station_price_history_fetchedAtEpochMillis`
                    ON `station_price_history` (`fetchedAtEpochMillis`)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `station_cache_snapshot` (
                        `latitudeBucket` INTEGER NOT NULL,
                        `longitudeBucket` INTEGER NOT NULL,
                        `radiusMeters` INTEGER NOT NULL,
                        `fuelType` TEXT NOT NULL,
                        `fetchedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`latitudeBucket`, `longitudeBucket`, `radiusMeters`, `fuelType`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `station_cache_snapshot` (
                        `latitudeBucket`,
                        `longitudeBucket`,
                        `radiusMeters`,
                        `fuelType`,
                        `fetchedAtEpochMillis`
                    )
                    SELECT
                        `latitudeBucket`,
                        `longitudeBucket`,
                        `radiusMeters`,
                        `fuelType`,
                        MAX(`fetchedAtEpochMillis`)
                    FROM `station_cache`
                    GROUP BY
                        `latitudeBucket`,
                        `longitudeBucket`,
                        `radiusMeters`,
                        `fuelType`
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_station_cache_latest_by_station`
                    ON `station_cache` (
                        `stationId`,
                        `fetchedAtEpochMillis`,
                        `fuelType`,
                        `radiusMeters`,
                        `latitudeBucket`,
                        `longitudeBucket`
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
