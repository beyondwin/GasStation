package com.gasstation.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GasStationDatabaseMigrationInstrumentedTest {
    @get:Rule(order = 0)
    val deviceEvidence = DeviceEvidenceReceiptRule()

    @get:Rule(order = 1)
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GasStationDatabase::class.java,
    )

    private val databaseName = "exported-schema-${System.nanoTime()}.db"

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun version1To5PreservesCacheAndDerivesSnapshotAtMaximumTimestamp() {
        helper.createDatabase(databaseName, 1).use { database ->
            insertCacheRow(database, stationId = "station-old", fetchedAtEpochMillis = 100L)
            insertCacheRow(database, stationId = "station-latest", fetchedAtEpochMillis = 200L)
        }

        helper.runMigrationsAndValidate(databaseName, 5, true, *allMigrations).use { database ->
            assertEquals(2, database.rowCount("station_cache"))
            assertCacheRow(database, stationId = "station-old", fetchedAtEpochMillis = 100L)
            assertCacheRow(database, stationId = "station-latest", fetchedAtEpochMillis = 200L)
            assertEquals(0, database.rowCount("station_price_history"))
            assertEquals(0, database.rowCount("watched_station"))
            assertSnapshotRow(database, fetchedAtEpochMillis = 200L)
            assertLatestByStationIndex(database)
        }
    }

    @Test
    fun version2To5PreservesCacheAndWatchButIntentionallyResetsPreFuelHistory() {
        helper.createDatabase(databaseName, 2).use { database ->
            insertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 300L)
            insertWatchedRow(database)
            insertVersion2HistoryRow(database)
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            GasStationDatabase.MIGRATION_2_3,
            GasStationDatabase.MIGRATION_3_4,
            GasStationDatabase.MIGRATION_4_5,
        ).use { database ->
            assertEquals(1, database.rowCount("station_cache"))
            assertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 300L)
            assertEquals(1, database.rowCount("watched_station"))
            assertWatchedRow(database)
            assertEquals(0, database.rowCount("station_price_history"))
            assertSnapshotRow(database, fetchedAtEpochMillis = 300L)
            assertLatestByStationIndex(database)
        }
    }

    @Test
    fun version2To3IntentionallyResetsHistoryWhilePreservingCacheAndWatch() {
        helper.createDatabase(databaseName, 2).use { database ->
            insertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 400L)
            insertWatchedRow(database)
            insertVersion2HistoryRow(database)
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            GasStationDatabase.MIGRATION_2_3,
        ).use { database ->
            assertEquals(1, database.rowCount("station_cache"))
            assertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 400L)
            assertEquals(1, database.rowCount("watched_station"))
            assertWatchedRow(database)
            assertEquals(0, database.rowCount("station_price_history"))
        }
    }

    @Test
    fun version3To5PreservesFuelScopedHistoryCacheAndWatch() {
        helper.createDatabase(databaseName, 3).use { database ->
            insertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 500L)
            insertWatchedRow(database)
            insertVersion3HistoryRow(database)
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            GasStationDatabase.MIGRATION_3_4,
            GasStationDatabase.MIGRATION_4_5,
        ).use { database ->
            assertEquals(1, database.rowCount("station_cache"))
            assertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 500L)
            assertEquals(1, database.rowCount("watched_station"))
            assertWatchedRow(database)
            assertEquals(1, database.rowCount("station_price_history"))
            assertHistoryRow(database)
            assertSnapshotRow(database, fetchedAtEpochMillis = 500L)
            assertLatestByStationIndex(database)
        }
    }

    @Test
    fun version4To5PreservesCacheWatchHistoryAndSnapshot() {
        helper.createDatabase(databaseName, 4).use { database ->
            insertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 600L)
            insertWatchedRow(database)
            insertVersion3HistoryRow(database)
            insertSnapshotRow(database, fetchedAtEpochMillis = 600L)
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            GasStationDatabase.MIGRATION_4_5,
        ).use { database ->
            assertEquals(1, database.rowCount("station_cache"))
            assertCacheRow(database, stationId = "station-1", fetchedAtEpochMillis = 600L)
            assertEquals(1, database.rowCount("watched_station"))
            assertWatchedRow(database)
            assertEquals(1, database.rowCount("station_price_history"))
            assertHistoryRow(database)
            assertSnapshotRow(database, fetchedAtEpochMillis = 600L)
            assertLatestByStationIndex(database)
        }
    }

    @Test
    fun version4To5PreservesSuccessfulEmptySnapshotMarker() {
        helper.createDatabase(databaseName, 4).use { database ->
            insertSnapshotRow(database, fetchedAtEpochMillis = 700L)
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            GasStationDatabase.MIGRATION_4_5,
        ).use { database ->
            assertEquals(0, database.rowCount("station_cache"))
            assertSnapshotRow(database, fetchedAtEpochMillis = 700L)
            assertLatestByStationIndex(database)
        }
    }

    private fun insertCacheRow(database: SupportSQLiteDatabase, stationId: String, fetchedAtEpochMillis: Long) {
        database.execSQL(
            """
            INSERT INTO station_cache (
                latitudeBucket, longitudeBucket, radiusMeters, fuelType, stationId,
                brandCode, name, priceWon, latitude, longitude, fetchedAtEpochMillis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                16_649,
                50_811,
                3_000,
                "GASOLINE",
                stationId,
                "GSC",
                "Migrated Station",
                1_699,
                37.498095,
                127.027610,
                fetchedAtEpochMillis,
            ),
        )
    }

    private fun insertWatchedRow(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO watched_station (
                stationId, name, brandCode, latitude, longitude, watchedAtEpochMillis
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "station-1",
                "Migrated Watched Station",
                "GSC",
                37.498095,
                127.027610,
                650L,
            ),
        )
    }

    private fun insertVersion2HistoryRow(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO station_price_history (
                stationId, priceWon, fetchedAtEpochMillis
            ) VALUES (?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("station-1", 1_699, 300L),
        )
    }

    private fun insertVersion3HistoryRow(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO station_price_history (
                stationId, fuelType, priceWon, fetchedAtEpochMillis
            ) VALUES (?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("station-1", "GASOLINE", 1_699, 500L),
        )
    }

    private fun insertSnapshotRow(database: SupportSQLiteDatabase, fetchedAtEpochMillis: Long) {
        database.execSQL(
            """
            INSERT INTO station_cache_snapshot (
                latitudeBucket, longitudeBucket, radiusMeters, fuelType, fetchedAtEpochMillis
            ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(16_649, 50_811, 3_000, "GASOLINE", fetchedAtEpochMillis),
        )
    }

    private fun SupportSQLiteDatabase.rowCount(tableName: String): Int = query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun assertCacheRow(database: SupportSQLiteDatabase, stationId: String, fetchedAtEpochMillis: Long) {
        database.query(
            """
            SELECT latitudeBucket, longitudeBucket, radiusMeters, fuelType,
                   stationId, brandCode, name, priceWon, latitude, longitude,
                   fetchedAtEpochMillis
            FROM station_cache
            WHERE stationId = ?
            """.trimIndent(),
            arrayOf(stationId),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(16_649, cursor.getInt(0))
            assertEquals(50_811, cursor.getInt(1))
            assertEquals(3_000, cursor.getInt(2))
            assertEquals("GASOLINE", cursor.getString(3))
            assertEquals(stationId, cursor.getString(4))
            assertEquals("GSC", cursor.getString(5))
            assertEquals("Migrated Station", cursor.getString(6))
            assertEquals(1_699, cursor.getInt(7))
            assertEquals(37.498095, cursor.getDouble(8), DOUBLE_TOLERANCE)
            assertEquals(127.027610, cursor.getDouble(9), DOUBLE_TOLERANCE)
            assertEquals(fetchedAtEpochMillis, cursor.getLong(10))
            assertTrue(!cursor.moveToNext())
        }
    }

    private fun assertWatchedRow(database: SupportSQLiteDatabase) {
        database.query(
            """
            SELECT stationId, name, brandCode, latitude, longitude,
                   watchedAtEpochMillis
            FROM watched_station
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("station-1", cursor.getString(0))
            assertEquals("Migrated Watched Station", cursor.getString(1))
            assertEquals("GSC", cursor.getString(2))
            assertEquals(37.498095, cursor.getDouble(3), DOUBLE_TOLERANCE)
            assertEquals(127.027610, cursor.getDouble(4), DOUBLE_TOLERANCE)
            assertEquals(650L, cursor.getLong(5))
            assertTrue(!cursor.moveToNext())
        }
    }

    private fun assertHistoryRow(database: SupportSQLiteDatabase) {
        database.query(
            """
            SELECT stationId, fuelType, priceWon, fetchedAtEpochMillis
            FROM station_price_history
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("station-1", cursor.getString(0))
            assertEquals("GASOLINE", cursor.getString(1))
            assertEquals(1_699, cursor.getInt(2))
            assertEquals(500L, cursor.getLong(3))
            assertTrue(!cursor.moveToNext())
        }
    }

    private fun assertSnapshotRow(database: SupportSQLiteDatabase, fetchedAtEpochMillis: Long) {
        assertEquals(1, database.rowCount("station_cache_snapshot"))
        database.query(
            """
            SELECT latitudeBucket, longitudeBucket, radiusMeters, fuelType,
                   fetchedAtEpochMillis
            FROM station_cache_snapshot
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(16_649, cursor.getInt(0))
            assertEquals(50_811, cursor.getInt(1))
            assertEquals(3_000, cursor.getInt(2))
            assertEquals("GASOLINE", cursor.getString(3))
            assertEquals(fetchedAtEpochMillis, cursor.getLong(4))
            assertTrue(!cursor.moveToNext())
        }
    }

    private fun assertLatestByStationIndex(database: SupportSQLiteDatabase) {
        val indexName = "index_station_cache_latest_by_station"
        val columns = database.query("PRAGMA index_info(`$indexName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }

        assertTrue(columns.isNotEmpty())
        assertEquals(
            listOf(
                "stationId",
                "fetchedAtEpochMillis",
                "fuelType",
                "radiusMeters",
                "latitudeBucket",
                "longitudeBucket",
            ),
            columns,
        )
    }

    private companion object {
        const val DOUBLE_TOLERANCE = 0.0000001

        val allMigrations = arrayOf(
            GasStationDatabase.MIGRATION_1_2,
            GasStationDatabase.MIGRATION_2_3,
            GasStationDatabase.MIGRATION_3_4,
            GasStationDatabase.MIGRATION_4_5,
        )
    }
}
