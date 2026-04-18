package com.gasstation

import android.content.Context
import androidx.room.Room
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.runBlocking

object DemoSeedData {
    private const val CACHE_BUCKET_METERS = 250

    private val reviewerCoordinates = Coordinates(
        latitude = 37.498095,
        longitude = 127.02761,
    )

    fun seed(context: Context) {
        val database = Room.databaseBuilder(
            context.applicationContext,
            GasStationDatabase::class.java,
            GasStationDatabase.DATABASE_NAME,
        ).fallbackToDestructiveMigration()
            .build()

        runBlocking {
            val cacheKey = StationQuery(
                coordinates = reviewerCoordinates,
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
                brandFilter = BrandFilter.ALL,
                sortOrder = SortOrder.DISTANCE,
                mapProvider = MapProvider.TMAP,
            ).toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
            val fetchedAt = System.currentTimeMillis()

            database.stationCacheDao().replaceSnapshot(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType.name,
                entities = demoStations(cacheKey, fetchedAt),
            )
        }

        database.close()
    }

    private fun demoStations(
        cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey,
        fetchedAtEpochMillis: Long,
    ): List<StationCacheEntity> = listOf(
        StationCacheEntity(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
            stationId = "demo-1",
            brandCode = "SKE",
            name = "강남역 데모 주유소",
            priceWon = 1_639,
            latitude = 37.49761,
            longitude = 127.02874,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        ),
        StationCacheEntity(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
            stationId = "demo-2",
            brandCode = "HDO",
            name = "테헤란로 셀프 주유소",
            priceWon = 1_657,
            latitude = 37.49911,
            longitude = 127.03012,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        ),
        StationCacheEntity(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
            stationId = "demo-3",
            brandCode = "GSC",
            name = "역삼 센트럴 주유소",
            priceWon = 1_671,
            latitude = 37.49682,
            longitude = 127.02655,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        ),
        StationCacheEntity(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
            stationId = "demo-4",
            brandCode = "SOL",
            name = "선릉 데모 충전소",
            priceWon = 1_689,
            latitude = 37.50024,
            longitude = 127.02718,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        ),
    )
}
