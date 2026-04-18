package com.gasstation.startup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.demo.seed.DemoSeedAssetLoader
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DemoSeedStartupHookTest {
    @Test
    fun `startup hook seeds deterministic snapshots, history, clears watched rows, and resets demo preferences`() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val assetLoader = DemoSeedAssetLoader()
        val document = assetLoader.load(application)
        val settingsRepository = FakeSettingsRepository(UserPreferences.default())

        application.deleteDatabase(GasStationDatabase.DATABASE_NAME)

        try {
            settingsRepository.updateUserPreferences {
                UserPreferences(
                    searchRadius = SearchRadius.KM_5,
                    fuelType = FuelType.DIESEL,
                    brandFilter = BrandFilter.SOL,
                    sortOrder = SortOrder.PRICE,
                    mapProvider = MapProvider.NAVER_MAP,
                )
            }

            val hook = DemoSeedStartupHook(assetLoader, settingsRepository)
            hook.run(application)

            val database = Room.databaseBuilder(
                application,
                GasStationDatabase::class.java,
                GasStationDatabase.DATABASE_NAME,
            ).allowMainThreadQueries().build()
            try {
                database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO watched_station (
                        stationId,
                        name,
                        brandCode,
                        latitude,
                        longitude,
                        watchedAtEpochMillis
                    ) VALUES (
                        'watched-demo',
                        'Watched Demo',
                        'SKE',
                        37.497927,
                        127.027583,
                        1770000000000
                    )
                    """.trimIndent(),
                )
                hook.seedDatabase(database = database, document = document)

                assertEquals(
                    document.queries.size,
                    database.openHelper.readableDatabase.singleInt(
                        """
                        SELECT COUNT(*) FROM (
                            SELECT DISTINCT radiusMeters, fuelType
                            FROM station_cache
                        )
                        """.trimIndent(),
                    ),
                )
                assertTrue(
                    database.openHelper.readableDatabase.singleInt(
                        "SELECT COUNT(*) FROM station_cache",
                    ) > 0,
                )
                assertEquals(
                    document.history.sumOf { it.entries.size },
                    database.openHelper.readableDatabase.singleInt(
                        "SELECT COUNT(*) FROM station_price_history",
                    ),
                )
                assertEquals(
                    document.history.size,
                    database.openHelper.readableDatabase.singleInt(
                        """
                        SELECT COUNT(*) FROM (
                            SELECT stationId, fuelType
                            FROM station_price_history
                            GROUP BY stationId, fuelType
                            HAVING COUNT(*) = 3
                        )
                        """.trimIndent(),
                    ),
                )
                assertEquals(
                    0,
                    database.openHelper.readableDatabase.singleInt(
                        "SELECT COUNT(*) FROM watched_station",
                    ),
                )
                assertEquals(UserPreferences.default(), settingsRepository.observeUserPreferences().first())
            } finally {
                database.close()
            }
        } finally {
            application.deleteDatabase(GasStationDatabase.DATABASE_NAME)
        }
    }
}

private class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.singleInt(sql: String): Int {
    val cursor = query(sql)
    return try {
        cursor.moveToFirst()
        cursor.getInt(0)
    } finally {
        cursor.close()
    }
}
