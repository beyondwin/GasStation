package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheDao
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertThrows
import kotlin.math.roundToInt

class DefaultStationRepositoryTest {
    private val now = Instant.parse("2026-04-18T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `refreshNearbyStations replaces the cached snapshot for the bucket`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val otherKey = cacheKey.copy(latitudeBucket = cacheKey.latitudeBucket + 1)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "stale-station",
                fetchedAt = now.minusSeconds(600),
            ),
            stationEntity(
                cacheKey = otherKey,
                stationId = "other-bucket",
                fetchedAt = now.minusSeconds(600),
            ),
        )
        val repository = DefaultStationRepository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(
                result = RemoteStationFetchResult.Success(
                    listOf(
                    RemoteStation(
                        stationId = "station-1",
                        name = "Gangnam First",
                        brandCode = "GSC",
                        priceWon = 1_689,
                        coordinates = Coordinates(37.499095, 127.027610),
                    ),
                    ),
                ),
            ),
            cachePolicy = StationCachePolicy(),
            clock = clock,
        )

        repository.refreshNearbyStations(query)

        val refreshedStations = stationCacheDao.snapshotFor(cacheKey)
        val unrelatedStations = stationCacheDao.snapshotFor(otherKey)

        assertEquals(1, stationCacheDao.replaceSnapshotCalls.size)
        assertEquals(listOf("station-1"), refreshedStations.map { it.stationId })
        assertEquals(listOf("other-bucket"), unrelatedStations.map { it.stationId })
        assertEquals(now.toEpochMilli(), refreshedStations.single().fetchedAtEpochMillis)
    }

    @Test
    fun `observeNearbyStations filters by brand and sorts by price client side`() = runBlocking {
        val query = stationQuery(
            brandFilter = BrandFilter.GSC,
            sortOrder = SortOrder.PRICE,
        )
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "cheap-gsc",
                brandCode = "GSC",
                priceWon = 1_610,
                latitude = 37.499095,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "expensive-gsc",
                brandCode = "GSC",
                priceWon = 1_710,
                latitude = 37.498295,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "filtered-out",
                brandCode = "SKE",
                priceWon = 1_400,
                latitude = 37.498195,
            ),
        )
        val repository = DefaultStationRepository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(RemoteStationFetchResult.Success(emptyList())),
            cachePolicy = StationCachePolicy(),
            clock = clock,
        )

        val result = repository.observeNearbyStations(query).first()

        assertEquals(listOf("cheap-gsc", "expensive-gsc"), result.stations.map { it.id })
        assertEquals(StationFreshness.Fresh, result.freshness)
        assertEquals(now, result.fetchedAt)
    }

    @Test
    fun `observeNearbyStations recalculates distance from the current query origin`() = runBlocking {
        val baseQuery = stationQuery(sortOrder = SortOrder.DISTANCE)
        val shiftedQuery = baseQuery.copy(
            coordinates = Coordinates(
                latitude = baseQuery.coordinates.latitude + 0.0007,
                longitude = baseQuery.coordinates.longitude,
            ),
        )
        val cacheKey = baseQuery.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "cheap",
                brandCode = "GSC",
                priceWon = 1_500,
                latitude = baseQuery.coordinates.latitude + 0.0010,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "mid",
                brandCode = "GSC",
                priceWon = 1_600,
                latitude = baseQuery.coordinates.latitude + 0.0002,
            ),
            stationEntity(
                cacheKey = cacheKey,
                stationId = "other-brand",
                brandCode = "SKE",
                priceWon = 1_450,
                latitude = baseQuery.coordinates.latitude + 0.0001,
            ),
        )
        val repository = DefaultStationRepository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(RemoteStationFetchResult.Success(emptyList())),
            cachePolicy = StationCachePolicy(),
            clock = clock,
        )

        val baseResult = repository.observeNearbyStations(baseQuery).first()
        val shiftedResult = repository.observeNearbyStations(shiftedQuery).first()

        assertEquals(listOf("other-brand", "mid", "cheap"), baseResult.stations.map { it.id })
        assertEquals(listOf("cheap", "mid", "other-brand"), shiftedResult.stations.map { it.id })
        assertEquals(
            expectedDistanceMeters(
                origin = shiftedQuery.coordinates,
                destination = Coordinates(
                    latitude = shiftedQuery.coordinates.latitude + 0.0003,
                    longitude = shiftedQuery.coordinates.longitude,
                ),
            ),
            shiftedResult.stations.first().distance.value,
        )
        assertTrue(shiftedResult.stations.first().distance.value < baseResult.stations.last().distance.value)
    }

    @Test
    fun `refreshNearbyStations preserves existing snapshot when remote fetch fails`() = runBlocking {
        val query = stationQuery()
        val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
        val stationCacheDao = RecordingStationCacheDao()
        stationCacheDao.seed(
            stationEntity(
                cacheKey = cacheKey,
                stationId = "cached-station",
                brandCode = "GSC",
                priceWon = 1_650,
                fetchedAt = now.minusSeconds(180),
            ),
        )
        val repository = DefaultStationRepository(
            stationCacheDao = stationCacheDao,
            remoteDataSource = FakeStationRemoteDataSource(RemoteStationFetchResult.Failure),
            cachePolicy = StationCachePolicy(),
            clock = clock,
        )

        assertThrows(StationRefreshException::class.java) {
            runBlocking {
                repository.refreshNearbyStations(query)
            }
        }

        val cachedStations = stationCacheDao.snapshotFor(cacheKey)

        assertEquals(0, stationCacheDao.replaceSnapshotCalls.size)
        assertEquals(listOf("cached-station"), cachedStations.map { it.stationId })
        assertEquals(now.minusSeconds(180).toEpochMilli(), cachedStations.single().fetchedAtEpochMillis)
    }

    private fun stationQuery(
        brandFilter: BrandFilter = BrandFilter.ALL,
        sortOrder: SortOrder = SortOrder.DISTANCE,
    ) = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = brandFilter,
        sortOrder = sortOrder,
        mapProvider = MapProvider.TMAP,
    )

    private fun stationEntity(
        cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey,
        stationId: String,
        brandCode: String = "GSC",
        priceWon: Int = 1_699,
        latitude: Double = 37.498095,
        longitude: Double = 127.027610,
        fetchedAt: Instant = now,
    ) = StationCacheEntity(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType.name,
        stationId = stationId,
        brandCode = brandCode,
        name = "Station $stationId",
        priceWon = priceWon,
        latitude = latitude,
        longitude = longitude,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )

    private fun expectedDistanceMeters(
        origin: Coordinates,
        destination: Coordinates,
    ): Int {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toRadians(destination.latitude - origin.latitude)
        val longitudeDelta = Math.toRadians(destination.longitude - origin.longitude)
        val originLatitudeRadians = Math.toRadians(origin.latitude)
        val destinationLatitudeRadians = Math.toRadians(destination.latitude)
        val haversine = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
            kotlin.math.cos(originLatitudeRadians) *
            kotlin.math.cos(destinationLatitudeRadians) *
            kotlin.math.sin(longitudeDelta / 2).let { it * it }
        val centralAngle = 2 * kotlin.math.atan2(kotlin.math.sqrt(haversine), kotlin.math.sqrt(1 - haversine))
        return (earthRadiusMeters * centralAngle).roundToInt()
    }

    private class FakeStationRemoteDataSource(
        private val result: RemoteStationFetchResult,
    ) : StationRemoteDataSource {
        override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult = result
    }

    private class RecordingStationCacheDao : StationCacheDao() {
        private val entities = MutableStateFlow<List<StationCacheEntity>>(emptyList())
        val replaceSnapshotCalls = mutableListOf<List<StationCacheEntity>>()

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

        override suspend fun upsertAll(entities: List<StationCacheEntity>) {
            this.entities.value = this.entities.value + entities
        }

        override suspend fun deleteStations(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
        ) {
            entities.value = entities.value.filterNot {
                it.latitudeBucket == latitudeBucket &&
                    it.longitudeBucket == longitudeBucket &&
                    it.radiusMeters == radiusMeters &&
                    it.fuelType == fuelType
            }
        }

        override suspend fun replaceSnapshot(
            latitudeBucket: Int,
            longitudeBucket: Int,
            radiusMeters: Int,
            fuelType: String,
            entities: List<StationCacheEntity>,
        ) {
            replaceSnapshotCalls += listOf(entities)
            super.replaceSnapshot(
                latitudeBucket = latitudeBucket,
                longitudeBucket = longitudeBucket,
                radiusMeters = radiusMeters,
                fuelType = fuelType,
                entities = entities,
            )
        }

        override suspend fun pruneOlderThan(cutoffEpochMillis: Long) = Unit

        fun seed(vararg entities: StationCacheEntity) {
            this.entities.value = entities.toList()
        }

        suspend fun snapshotFor(
            cacheKey: com.gasstation.domain.station.model.StationQueryCacheKey,
        ): List<StationCacheEntity> = observeStations(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
        ).first()
    }

    private companion object {
        const val CACHE_BUCKET_METERS = 250
    }
}
