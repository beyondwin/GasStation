package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.data.station.mapper.toDomainStation
import com.gasstation.data.station.mapper.toEntity
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultStationRepository @Inject constructor(
    private val stationCacheDao: StationCacheDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val cachePolicy: StationCachePolicy,
    private val clock: Clock,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)

        return stationCacheDao.observeStations(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
        ).map { cachedStations ->
            val fetchedAt = cachedStations.maxOfOrNull { it.fetchedAtEpochMillis }?.let(Instant::ofEpochMilli)
            val stations = cachedStations
                .map { it.toDomainStation(query.coordinates) }
                .filter { query.brandFilter.matches(it.brand) }
                .let { unsorted ->
                    when (query.sortOrder) {
                        SortOrder.DISTANCE -> unsorted.sortedBy { it.distance.value }
                        SortOrder.PRICE -> unsorted.sortedBy { it.price.value }
                    }
                }

            StationSearchResult(
                stations = stations,
                freshness = fetchedAt?.let { cachePolicy.freshnessOf(it, clock.instant()) }
                    ?: StationFreshness.Stale,
                fetchedAt = fetchedAt,
            )
        }
    }

    override suspend fun refreshNearbyStations(query: StationQuery) {
        val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
        val fetchedAt = clock.instant()
        when (val remoteStations = remoteDataSource.fetchStations(query)) {
            is RemoteStationFetchResult.Failure -> {
                throw StationRefreshException()
            }
            is RemoteStationFetchResult.Success -> {
                stationCacheDao.replaceSnapshot(
                    latitudeBucket = cacheKey.latitudeBucket,
                    longitudeBucket = cacheKey.longitudeBucket,
                    radiusMeters = cacheKey.radiusMeters,
                    fuelType = cacheKey.fuelType.name,
                    entities = remoteStations.stations.map { it.toEntity(cacheKey, fetchedAt) },
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_BUCKET_METERS = 250
    }
}

class StationRefreshException : IllegalStateException("Failed to refresh nearby stations")
