package com.gasstation.core.database.station

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StationPriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StationPriceHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<StationPriceHistoryEntity>)

    @Query(
        """
        SELECT * FROM station_price_history
        WHERE stationId IN (:stationIds)
        ORDER BY stationId ASC, fuelType ASC, fetchedAtEpochMillis DESC
        """,
    )
    fun observeByStationIds(stationIds: List<String>): Flow<List<StationPriceHistoryEntity>>

    @Query(
        """
        SELECT * FROM station_price_history
        WHERE stationId IN (:stationIds)
          AND fuelType = :fuelType
        ORDER BY stationId ASC, fuelType ASC, fetchedAtEpochMillis DESC
        """,
    )
    fun observeByStationIdsAndFuelType(
        stationIds: List<String>,
        fuelType: String,
    ): Flow<List<StationPriceHistoryEntity>>

    @Query(
        """
        DELETE FROM station_price_history
        WHERE stationId = :stationId
          AND fuelType = :fuelType
          AND fetchedAtEpochMillis NOT IN (
              SELECT fetchedAtEpochMillis
              FROM station_price_history
              WHERE stationId = :stationId
                AND fuelType = :fuelType
              ORDER BY fetchedAtEpochMillis DESC
              LIMIT 10
          )
        """,
    )
    suspend fun keepLatestTenByStationAndFuelType(
        stationId: String,
        fuelType: String,
    )
}
