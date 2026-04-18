package com.gasstation.core.database.station

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StationCacheDao {
    @Query(
        """
        SELECT * FROM station_cache
        WHERE latitudeBucket = :latitudeBucket
          AND longitudeBucket = :longitudeBucket
          AND radiusMeters = :radiusMeters
          AND fuelType = :fuelType
        """,
    )
    abstract fun observeStations(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): Flow<List<StationCacheEntity>>

    @Query(
        """
        SELECT * FROM station_cache
        WHERE stationId IN (:stationIds)
        ORDER BY stationId ASC,
                 fetchedAtEpochMillis DESC,
                 fuelType ASC,
                 radiusMeters ASC,
                 latitudeBucket ASC,
                 longitudeBucket ASC
        """,
    )
    abstract fun observeLatestStationsByIds(
        stationIds: List<String>,
    ): Flow<List<StationCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<StationCacheEntity>)

    @Query(
        """
        DELETE FROM station_cache
        WHERE latitudeBucket = :latitudeBucket
          AND longitudeBucket = :longitudeBucket
          AND radiusMeters = :radiusMeters
          AND fuelType = :fuelType
        """,
    )
    protected abstract suspend fun deleteStations(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    )

    @Transaction
    open suspend fun replaceSnapshot(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
        entities: List<StationCacheEntity>,
    ) {
        require(
            entities.all { entity ->
                entity.latitudeBucket == latitudeBucket &&
                    entity.longitudeBucket == longitudeBucket &&
                    entity.radiusMeters == radiusMeters &&
                    entity.fuelType == fuelType
            },
        ) { "All entities must belong to the same cache bucket snapshot" }

        deleteStations(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radiusMeters,
            fuelType = fuelType,
        )
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }

    @Query(
        """
        DELETE FROM station_cache
        WHERE fetchedAtEpochMillis < :cutoffEpochMillis
        """,
    )
    abstract suspend fun pruneOlderThan(cutoffEpochMillis: Long)
}
