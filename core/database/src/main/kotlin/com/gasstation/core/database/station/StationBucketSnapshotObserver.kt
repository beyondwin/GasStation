package com.gasstation.core.database.station

import androidx.room.withTransaction
import com.gasstation.core.database.GasStationDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

fun interface StationBucketSnapshotObserver {
    fun observe(latitudeBucket: Int, longitudeBucket: Int, radiusMeters: Int, fuelType: String): Flow<StationBucketSnapshot>
}

class RoomStationBucketSnapshotObserver @Inject constructor(private val database: GasStationDatabase) : StationBucketSnapshotObserver {
    override fun observe(latitudeBucket: Int, longitudeBucket: Int, radiusMeters: Int, fuelType: String): Flow<StationBucketSnapshot> =
        database.invalidationTracker
            .createFlow("station_cache", "station_cache_snapshot")
            .map {
                database.withTransaction {
                    val dao = database.stationCacheDao()
                    val marker = dao.readSnapshot(
                        latitudeBucket = latitudeBucket,
                        longitudeBucket = longitudeBucket,
                        radiusMeters = radiusMeters,
                        fuelType = fuelType,
                    )
                    val rows = if (marker == null) {
                        emptyList()
                    } else {
                        dao.readStations(
                            latitudeBucket = latitudeBucket,
                            longitudeBucket = longitudeBucket,
                            radiusMeters = radiusMeters,
                            fuelType = fuelType,
                        )
                    }
                    check(
                        rows.all { row ->
                            row.fetchedAtEpochMillis == marker?.fetchedAtEpochMillis
                        },
                    ) {
                        "Station cache rows must share the bucket snapshot timestamp"
                    }
                    StationBucketSnapshot(marker = marker, rows = rows)
                }
            }
            .distinctUntilChanged()
}
