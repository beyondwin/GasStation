package com.gasstation.data.station

import com.gasstation.core.database.station.StationBucketSnapshot
import com.gasstation.core.database.station.StationBucketSnapshotObserver
import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.database.station.StationCacheSnapshotEntity
import com.gasstation.core.observability.CrashReporter
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class FakeStationRemoteDataSource(private val result: RemoteStationFetchResult) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = result
}

internal class ThrowingStationRemoteDataSource(private val throwable: Throwable) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = throw throwable
}

internal class FakeSeedStationRemoteDataSource(private val result: RemoteStationFetchResult) : SeedStationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = result
}

internal class QueueStationRemoteDataSource(private val results: ArrayDeque<RemoteStationFetchResult>) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = results.removeFirst()
}

internal object RepositoryDoubles {
    internal class RecordingStationEventLogger : StationEventLogger {
        val events = mutableListOf<StationEvent>()

        override fun log(event: StationEvent) {
            events += event
        }
    }

    internal class ThrowingStationEventLogger : StationEventLogger {
        override fun log(event: StationEvent): Unit = throw IllegalStateException("analytics failed")
    }
}

internal class FakeCrashReporter : CrashReporter {
    data class Record(val throwable: Throwable, val metadata: Map<String, String>)
    val records = mutableListOf<Record>()
    val logs = mutableListOf<String>()
    override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
        records += Record(throwable, metadata)
    }
    override fun log(message: String) {
        logs += message
    }
}

internal class RecordingStationCacheDao : StationCacheDao() {
    data class ReplaceSnapshotCall(
        val latitudeBucket: Int,
        val longitudeBucket: Int,
        val radiusMeters: Int,
        val fuelType: String,
        val fetchedAtEpochMillis: Long,
        val entities: List<StationCacheEntity>,
    )

    private val entities = MutableStateFlow<List<StationCacheEntity>>(emptyList())
    private val snapshots = MutableStateFlow<List<StationCacheSnapshotEntity>>(emptyList())
    val replaceSnapshotCalls = mutableListOf<List<StationCacheEntity>>()
    val replaceSnapshotRecords = mutableListOf<ReplaceSnapshotCall>()
    val pruneCutoffCalls = mutableListOf<Long>()

    override fun observeStations(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): Flow<List<StationCacheEntity>> = entities.map { current ->
        current.filter {
            it.latitudeBucket == latitudeBucket &&
                it.longitudeBucket == longitudeBucket &&
                it.radiusMeters == radiusMeters &&
                it.fuelType == fuelType
        }
    }

    override fun observeLatestStationsByIds(stationIds: List<String>): Flow<List<StationCacheEntity>> = entities.map { current ->
        current
            .filter { it.stationId in stationIds }
            .groupBy { it.stationId }
            .values
            .map { rows -> rows.maxBy { it.fetchedAtEpochMillis } }
    }

    override fun observeLatestStationsByIdsAndFuelType(stationIds: List<String>, fuelType: String): Flow<List<StationCacheEntity>> =
        entities.map { current ->
            current
                .filter { it.stationId in stationIds && it.fuelType == fuelType }
                .groupBy { it.stationId }
                .values
                .map { rows ->
                    rows.sortedWith(
                        compareByDescending<StationCacheEntity> { it.fetchedAtEpochMillis }
                            .thenBy { it.radiusMeters }
                            .thenBy { it.latitudeBucket }
                            .thenBy { it.longitudeBucket },
                    ).first()
                }
        }

    override fun observeSnapshot(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): Flow<StationCacheSnapshotEntity?> = snapshots.map { current ->
        current.firstOrNull {
            it.latitudeBucket == latitudeBucket &&
                it.longitudeBucket == longitudeBucket &&
                it.radiusMeters == radiusMeters &&
                it.fuelType == fuelType
        }
    }

    override suspend fun readStations(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): List<StationCacheEntity> = entities.value.filter {
        it.latitudeBucket == latitudeBucket &&
            it.longitudeBucket == longitudeBucket &&
            it.radiusMeters == radiusMeters &&
            it.fuelType == fuelType
    }

    override suspend fun readSnapshot(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): StationCacheSnapshotEntity? = snapshots.value.firstOrNull {
        it.latitudeBucket == latitudeBucket &&
            it.longitudeBucket == longitudeBucket &&
            it.radiusMeters == radiusMeters &&
            it.fuelType == fuelType
    }

    override suspend fun upsertAll(entities: List<StationCacheEntity>) {
        this.entities.value = this.entities.value + entities
    }

    override suspend fun deleteStations(latitudeBucket: Int, longitudeBucket: Int, radiusMeters: Int, fuelType: String) {
        entities.value = entities.value.filterNot {
            it.latitudeBucket == latitudeBucket &&
                it.longitudeBucket == longitudeBucket &&
                it.radiusMeters == radiusMeters &&
                it.fuelType == fuelType
        }
    }

    override suspend fun upsertSnapshot(snapshot: StationCacheSnapshotEntity) {
        snapshots.value = snapshots.value
            .filterNot {
                it.latitudeBucket == snapshot.latitudeBucket &&
                    it.longitudeBucket == snapshot.longitudeBucket &&
                    it.radiusMeters == snapshot.radiusMeters &&
                    it.fuelType == snapshot.fuelType
            } + snapshot
    }

    override suspend fun replaceSnapshot(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
        fetchedAtEpochMillis: Long,
        entities: List<StationCacheEntity>,
    ) {
        replaceSnapshotCalls += listOf(entities)
        replaceSnapshotRecords += ReplaceSnapshotCall(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radiusMeters,
            fuelType = fuelType,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            entities = entities,
        )
        super.replaceSnapshot(
            latitudeBucket = latitudeBucket,
            longitudeBucket = longitudeBucket,
            radiusMeters = radiusMeters,
            fuelType = fuelType,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            entities = entities,
        )
    }

    override suspend fun pruneStationsOlderThan(cutoffEpochMillis: Long) {
        entities.value = entities.value.filterNot { it.fetchedAtEpochMillis < cutoffEpochMillis }
    }

    override suspend fun pruneSnapshotsOlderThan(cutoffEpochMillis: Long) {
        snapshots.value = snapshots.value.filterNot { it.fetchedAtEpochMillis < cutoffEpochMillis }
    }

    override suspend fun pruneOlderThan(cutoffEpochMillis: Long) {
        pruneCutoffCalls += cutoffEpochMillis
        super.pruneOlderThan(cutoffEpochMillis)
    }

    fun seed(vararg entities: StationCacheEntity) {
        this.entities.value = entities.toList()
        snapshots.value = entities
            .groupBy { listOf(it.latitudeBucket, it.longitudeBucket, it.radiusMeters, it.fuelType) }
            .values
            .map { bucketRows ->
                val first = bucketRows.first()
                StationCacheSnapshotEntity(
                    latitudeBucket = first.latitudeBucket,
                    longitudeBucket = first.longitudeBucket,
                    radiusMeters = first.radiusMeters,
                    fuelType = first.fuelType,
                    fetchedAtEpochMillis = bucketRows.maxOf { it.fetchedAtEpochMillis },
                )
            }
    }

    suspend fun snapshotFor(cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey): List<StationCacheEntity> = observeStations(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType.name,
    ).first()

    suspend fun markerFor(cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey): StationCacheSnapshotEntity? = readSnapshot(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType.name,
    )
}

internal class RecordingStationBucketSnapshotObserver(private val stationCacheDao: StationCacheDao) : StationBucketSnapshotObserver {
    override fun observe(latitudeBucket: Int, longitudeBucket: Int, radiusMeters: Int, fuelType: String): Flow<StationBucketSnapshot> =
        kotlinx.coroutines.flow.combine(
            stationCacheDao.observeSnapshot(
                latitudeBucket = latitudeBucket,
                longitudeBucket = longitudeBucket,
                radiusMeters = radiusMeters,
                fuelType = fuelType,
            ),
            stationCacheDao.observeStations(
                latitudeBucket = latitudeBucket,
                longitudeBucket = longitudeBucket,
                radiusMeters = radiusMeters,
                fuelType = fuelType,
            ),
        ) { marker, rows ->
            StationBucketSnapshot(
                marker = marker,
                rows = if (marker == null) emptyList() else rows,
            )
        }
}
