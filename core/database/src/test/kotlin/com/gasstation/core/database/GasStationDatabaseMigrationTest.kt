package com.gasstation.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.station.StationCacheEntity
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
    fun `migration 1 to 2 preserves cache data and opens through Room`() = runBlocking {
        val db = helper.writableDatabase
        createVersion1Schema(db)
        insertVersion1CacheRow(db)
        helper.close()

        val migratedDatabase = Room.databaseBuilder(
            context,
            GasStationDatabase::class.java,
            databaseName,
        )
            .addMigrations(GasStationDatabase.MIGRATION_1_2)
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
                migratedDatabase.stationPriceHistoryDao().observeByStationIds(listOf("station-1")).first(),
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
