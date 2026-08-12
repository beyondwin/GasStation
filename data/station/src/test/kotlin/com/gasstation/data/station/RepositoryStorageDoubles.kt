package com.gasstation.data.station

import com.gasstation.core.database.DatabaseTransactionRunner
import com.gasstation.core.database.station.StationPriceHistoryDao
import com.gasstation.core.database.station.StationPriceHistoryEntity
import com.gasstation.core.database.station.WatchedStationDao
import com.gasstation.core.database.station.WatchedStationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

internal class ImmediateDatabaseTransactionRunner : DatabaseTransactionRunner {
    var invocations = 0
        private set

    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        invocations++
        return block()
    }
}

internal class RecordingStationPriceHistoryDao(history: List<StationPriceHistoryEntity> = emptyList()) : StationPriceHistoryDao {
    private val entities = MutableStateFlow(history.sortedHistory())

    val insertAllCalls = mutableListOf<List<StationPriceHistoryEntity>>()
    val keepLatestTenCalls = mutableListOf<Pair<String, String>>()

    override suspend fun insert(entity: StationPriceHistoryEntity) {
        insertAll(listOf(entity))
    }

    override suspend fun insertAll(entities: List<StationPriceHistoryEntity>) {
        insertAllCalls += listOf(entities)
        this.entities.value = (this.entities.value + entities).sortedHistory()
    }

    override fun observeByStationIds(stationIds: List<String>): Flow<List<StationPriceHistoryEntity>> = entities.map { current ->
        current
            .filter { it.stationId in stationIds }
            .sortedHistory()
    }

    override fun observeByStationIdsAndFuelType(stationIds: List<String>, fuelType: String): Flow<List<StationPriceHistoryEntity>> =
        entities.map { current ->
            current
                .filter { it.stationId in stationIds && it.fuelType == fuelType }
                .sortedHistory()
        }

    override suspend fun keepLatestTenByStationAndFuelType(stationId: String, fuelType: String) {
        keepLatestTenCalls += stationId to fuelType
        val retained = entities.value
            .filter { it.stationId == stationId && it.fuelType == fuelType }
            .sortedByDescending { it.fetchedAtEpochMillis }
            .take(10)
        val others = entities.value.filterNot { it.stationId == stationId && it.fuelType == fuelType }
        entities.value = (others + retained).sortedHistory()
    }

    fun entriesFor(stationId: String, fuelType: String? = null): List<StationPriceHistoryEntity> = entities.value
        .filter { it.stationId == stationId && (fuelType == null || it.fuelType == fuelType) }
        .sortedByDescending { it.fetchedAtEpochMillis }
}

internal class RecordingWatchedStationDao(watchedStations: List<WatchedStationEntity> = emptyList()) : WatchedStationDao {
    private val entities = MutableStateFlow(watchedStations.sortedWatched())

    val insertedEntities = mutableListOf<WatchedStationEntity>()
    val deletedStationIds = mutableListOf<String>()

    override suspend fun insertIfAbsent(entity: WatchedStationEntity): Long {
        insertedEntities += entity
        if (entities.value.any { it.stationId == entity.stationId }) return -1L
        entities.value = (entities.value + entity).sortedWatched()
        return entities.value.indexOfFirst { it.stationId == entity.stationId }.toLong() + 1L
    }

    override suspend fun delete(stationId: String) {
        deletedStationIds += stationId
        entities.value = entities.value.filterNot { it.stationId == stationId }.sortedWatched()
    }

    override fun observeWatchedStationIds(): Flow<List<String>> = entities.map { current ->
        current.sortedWatched().map { it.stationId }
    }

    override fun observeWatchedStations(): Flow<List<WatchedStationEntity>> = entities.map { current ->
        current.sortedWatched()
    }

    fun currentWatchedStations(): List<WatchedStationEntity> = entities.value.sortedWatched()
}

internal fun history(stationId: String, fuelType: String = "GASOLINE", priceWon: Int, fetchedAt: Instant) = StationPriceHistoryEntity(
    stationId = stationId,
    fuelType = fuelType,
    priceWon = priceWon,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

internal fun watched(
    stationId: String,
    watchedAt: Instant,
    name: String = "Station $stationId",
    brandCode: String = "GSC",
    latitude: Double = 37.498095,
    longitude: Double = 127.027610,
) = WatchedStationEntity(
    stationId = stationId,
    name = name,
    brandCode = brandCode,
    latitude = latitude,
    longitude = longitude,
    watchedAtEpochMillis = watchedAt.toEpochMilli(),
)

private fun List<StationPriceHistoryEntity>.sortedHistory(): List<StationPriceHistoryEntity> = sortedWith(
    compareBy<StationPriceHistoryEntity> { it.stationId }
        .thenBy { it.fuelType }
        .thenByDescending { it.fetchedAtEpochMillis },
)

private fun List<WatchedStationEntity>.sortedWatched(): List<WatchedStationEntity> = sortedWith(
    compareByDescending<WatchedStationEntity> { it.watchedAtEpochMillis }
        .thenBy { it.stationId },
)
