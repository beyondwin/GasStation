package com.gasstation.core.database.station

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchedStationEntity)

    @Query("DELETE FROM watched_station WHERE stationId = :stationId")
    suspend fun delete(stationId: String)

    @Query("SELECT stationId FROM watched_station ORDER BY watchedAtEpochMillis DESC")
    fun observeWatchedStationIds(): Flow<List<String>>

    @Query("SELECT * FROM watched_station ORDER BY watchedAtEpochMillis DESC")
    fun observeWatchedStations(): Flow<List<WatchedStationEntity>>
}
