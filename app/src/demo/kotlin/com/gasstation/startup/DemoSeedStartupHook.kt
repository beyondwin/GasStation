package com.gasstation.startup

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.demo.seed.DemoSeedAssetLoader
import com.gasstation.demo.seed.DemoSeedDocument
import com.gasstation.demo.seed.DemoSeedOrigin
import com.gasstation.demo.seed.DemoSeedQueryDocument
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.StationQueryCacheKey
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class DemoSeedStartupHook @Inject constructor(
    private val assetLoader: DemoSeedAssetLoader,
    private val settingsRepository: SettingsRepository,
) : AppStartupHook {
    override fun run(application: Application) {
        val document = assetLoader.load(application)
        val database = Room.databaseBuilder(
            application,
            GasStationDatabase::class.java,
            GasStationDatabase.DATABASE_NAME,
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        try {
            seedDatabase(
                database = database,
                document = document,
            )
            runBlocking {
                settingsRepository.updateUserPreferences { UserPreferences.default() }
            }
        } finally {
            database.close()
        }
    }

    fun seedDatabase(
        database: GasStationDatabase,
        document: DemoSeedDocument,
    ) {
        document.requireSharedOrigin()
        runBlocking {
            database.withTransaction {
                val writableDatabase = database.openHelper.writableDatabase
                val historyInsert = writableDatabase.compileStatement(
                    """
                    INSERT OR REPLACE INTO station_price_history (
                        stationId,
                        fuelType,
                        priceWon,
                        fetchedAtEpochMillis
                    ) VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                )

                writableDatabase.execSQL("DELETE FROM station_cache")
                writableDatabase.execSQL("DELETE FROM station_cache_snapshot")
                writableDatabase.execSQL("DELETE FROM station_price_history")
                writableDatabase.execSQL("DELETE FROM watched_station")

                document.queries.forEach { query ->
                    val cacheKey = query.toCacheKey()
                    val snapshotEntities = query.stations.map { station ->
                        StationCacheEntity(
                            latitudeBucket = cacheKey.latitudeBucket,
                            longitudeBucket = cacheKey.longitudeBucket,
                            radiusMeters = cacheKey.radiusMeters,
                            fuelType = query.fuelType,
                            stationId = station.stationId,
                            brandCode = station.brandCode,
                            name = station.name,
                            priceWon = station.priceWon,
                            latitude = station.latitude,
                            longitude = station.longitude,
                            fetchedAtEpochMillis = document.generatedAtEpochMillis,
                        )
                    }
                    database.stationCacheDao().replaceSnapshot(
                        latitudeBucket = cacheKey.latitudeBucket,
                        longitudeBucket = cacheKey.longitudeBucket,
                        radiusMeters = cacheKey.radiusMeters,
                        fuelType = query.fuelType,
                        fetchedAtEpochMillis = document.generatedAtEpochMillis,
                        entities = snapshotEntities,
                    )
                }

                document.history.forEach { history ->
                    history.entries.forEach { entry ->
                        historyInsert.clearBindings()
                        historyInsert.bindString(1, history.stationId)
                        historyInsert.bindString(2, history.fuelType)
                        historyInsert.bindLong(3, entry.priceWon.toLong())
                        historyInsert.bindLong(4, entry.fetchedAtEpochMillis)
                        historyInsert.executeInsert()
                    }
                }
            }
        }
    }

    private fun DemoSeedDocument.requireSharedOrigin() {
        require(origin.label == DemoSeedOrigin.label) {
            "Demo seed origin label must stay aligned with the demo location override."
        }
        require(
            origin.latitude == DemoSeedOrigin.coordinates.latitude &&
                origin.longitude == DemoSeedOrigin.coordinates.longitude,
        ) { "Demo seed origin coordinates must stay aligned with the demo location override." }
    }

    private fun DemoSeedQueryDocument.toCacheKey(): StationQueryCacheKey = StationQuery(
        coordinates = DemoSeedOrigin.coordinates,
        radius = SearchRadius.entries.single { it.meters == radiusMeters },
        fuelType = FuelType.valueOf(fuelType),
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
        mapProvider = MapProvider.TMAP,
    ).toCacheKey(bucketMeters = CACHE_BUCKET_METERS)

    private companion object {
        const val CACHE_BUCKET_METERS = 250
    }
}
