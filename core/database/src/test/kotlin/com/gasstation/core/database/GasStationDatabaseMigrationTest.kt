package com.gasstation.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.WatchedStationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GasStationDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "migration-test-${System.nanoTime()}.db"
        databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        if (databaseFile.exists()) {
            databaseFile.delete()
        }

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
    }

    @After
    fun tearDown() {
        if (::helper.isInitialized) {
            helper.close()
        }
        if (::databaseFile.isInitialized && databaseFile.exists()) {
            databaseFile.delete()
        }
    }

    @Test
    fun `migration 1 to 3 preserves cache data and opens through Room`() = runBlocking {
        val db = helper.writableDatabase
        createVersion1Schema(db)
        insertVersion1CacheRow(db)
        helper.close()

        val migratedDatabase = Room.databaseBuilder(
            context,
            GasStationDatabase::class.java,
            databaseName,
        )
            .addMigrations(
                GasStationDatabase.MIGRATION_1_2,
                GasStationDatabase.MIGRATION_2_3,
            )
            .allowMainThreadQueries()
            .build()

        try {
            val cacheRows = migratedDatabase.stationCacheDao().observeStations(
                latitudeBucket = 16649,
                longitudeBucket = 50811,
                radiusMeters = 3_000,
                fuelType = "GASOLINE",
            ).first()

            assertEquals(listOf(version1CacheRow()), cacheRows)
            assertEquals(
                emptyList<Any>(),
                migratedDatabase.stationPriceHistoryDao().observeByStationIdsAndFuelType(
                    stationIds = listOf("station-1"),
                    fuelType = "GASOLINE",
                ).first(),
            )
            assertEquals(
                emptyList<String>(),
                migratedDatabase.watchedStationDao().observeWatchedStationIds().first(),
            )
            assertTrue(tableExists(migratedDatabase.openHelper.writableDatabase, "station_price_history"))
            assertTrue(tableExists(migratedDatabase.openHelper.writableDatabase, "watched_station"))
        } finally {
            migratedDatabase.close()
        }
    }

    @Test
    fun `migration 2 to 3 preserves cache and watched rows while recreating price history`() = runBlocking {
        val db = helper.writableDatabase
        createVersion2Schema(db)
        insertVersion2CacheRow(db)
        insertVersion2WatchedRow(db)
        insertVersion2HistoryRow(db)
        helper.close()

        val migratedDatabase = Room.databaseBuilder(
            context,
            GasStationDatabase::class.java,
            databaseName,
        )
            .addMigrations(GasStationDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val cacheRows = migratedDatabase.stationCacheDao().observeStations(
                latitudeBucket = 16649,
                longitudeBucket = 50811,
                radiusMeters = 3_000,
                fuelType = "GASOLINE",
            ).first()
            val watchedRows = migratedDatabase.watchedStationDao().observeWatchedStations().first()
            val historyRows = migratedDatabase.stationPriceHistoryDao().observeByStationIds(listOf("station-1")).first()

            assertEquals(listOf(version1CacheRow()), cacheRows)
            assertEquals(listOf(version2WatchedRow()), watchedRows)
            assertEquals(emptyList<Any>(), historyRows)
            assertTrue(tableExists(migratedDatabase.openHelper.writableDatabase, "station_price_history"))
        } finally {
            migratedDatabase.close()
        }
    }

    private fun createVersion1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `station_cache` (
                `latitudeBucket` INTEGER NOT NULL,
                `longitudeBucket` INTEGER NOT NULL,
                `radiusMeters` INTEGER NOT NULL,
                `fuelType` TEXT NOT NULL,
                `stationId` TEXT NOT NULL,
                `brandCode` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `priceWon` INTEGER NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `fetchedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(
                    `latitudeBucket`,
                    `longitudeBucket`,
                    `radiusMeters`,
                    `fuelType`,
                    `stationId`
                )
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_station_cache_fetchedAtEpochMillis` " +
                "ON `station_cache` (`fetchedAtEpochMillis`)",
        )
    }

    private fun createVersion2Schema(db: SupportSQLiteDatabase) {
        createVersion1Schema(db)
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
        db.execSQL("PRAGMA user_version = 2")
    }

    private fun insertVersion1CacheRow(db: SupportSQLiteDatabase) {
        val row = version1CacheRow()
        db.execSQL(
            """
            INSERT INTO `station_cache` (
                `latitudeBucket`,
                `longitudeBucket`,
                `radiusMeters`,
                `fuelType`,
                `stationId`,
                `brandCode`,
                `name`,
                `priceWon`,
                `latitude`,
                `longitude`,
                `fetchedAtEpochMillis`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                row.latitudeBucket,
                row.longitudeBucket,
                row.radiusMeters,
                row.fuelType,
                row.stationId,
                row.brandCode,
                row.name,
                row.priceWon,
                row.latitude,
                row.longitude,
                row.fetchedAtEpochMillis,
            ),
        )
    }

    private fun insertVersion2HistoryRow(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO `station_price_history` (
                `stationId`,
                `priceWon`,
                `fetchedAtEpochMillis`
            ) VALUES (?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("station-1", 1699, 1_744_947_200_000L),
        )
    }

    private fun insertVersion2WatchedRow(db: SupportSQLiteDatabase) {
        val row = version2WatchedRow()
        db.execSQL(
            """
            INSERT INTO `watched_station` (
                `stationId`,
                `name`,
                `brandCode`,
                `latitude`,
                `longitude`,
                `watchedAtEpochMillis`
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                row.stationId,
                row.name,
                row.brandCode,
                row.latitude,
                row.longitude,
                row.watchedAtEpochMillis,
            ),
        )
    }

    private fun insertVersion2CacheRow(db: SupportSQLiteDatabase) {
        insertVersion1CacheRow(db)
    }

    private fun version1CacheRow(): StationCacheEntity = StationCacheEntity(
        latitudeBucket = 16649,
        longitudeBucket = 50811,
        radiusMeters = 3_000,
        fuelType = "GASOLINE",
        stationId = "station-1",
        brandCode = "GSC",
        name = "Migrated Station",
        priceWon = 1_699,
        latitude = 37.498095,
        longitude = 127.027610,
        fetchedAtEpochMillis = 1_744_947_200_000L,
    )

    private fun version2WatchedRow(): WatchedStationEntity = WatchedStationEntity(
        stationId = "station-1",
        name = "Migrated Watched Station",
        brandCode = "GSC",
        latitude = 37.498095,
        longitude = 127.027610,
        watchedAtEpochMillis = 1_744_947_260_000L,
    )

    private fun tableExists(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): Boolean = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(tableName),
    ).use { cursor ->
        cursor.moveToFirst()
    }
}
