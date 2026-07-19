package com.gasstation.data.station

import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class StationSearchResultAssemblerTest {
    private val now = Instant.parse("2026-04-18T03:00:00Z")
    private val query = StationQuery(
        coordinates = Coordinates(37.498095, 127.027610),
        radius = SearchRadius.KM_3,
        fuelType = FuelType.GASOLINE,
        brandFilter = BrandFilter.ALL,
        sortOrder = SortOrder.DISTANCE,
    )

    @Test
    fun `toSearchResult skips rows with out-of-range coordinates and keeps valid rows`() {
        val rows = listOf(
            cacheRow(stationId = "ok", latitude = 37.499095, longitude = 127.028610),
            cacheRow(stationId = "bad-coord", latitude = 200.0, longitude = 127.028610),
        )

        val result = rows.toSearchResult(
            query = query,
            watchedStationIds = emptySet(),
            historyRowsByStationId = emptyMap(),
            fetchedAt = now,
            cachePolicy = StationCachePolicy(),
            now = now,
        )

        assertEquals(listOf("ok"), result.stations.map { it.station.id })
    }

    @Test
    fun `toSearchResult skips rows with negative price and keeps valid rows`() {
        val rows = listOf(
            cacheRow(stationId = "ok", latitude = 37.499095, longitude = 127.028610),
            cacheRow(stationId = "bad-price", latitude = 37.499095, longitude = 127.028610, priceWon = -1),
        )

        val result = rows.toSearchResult(
            query = query,
            watchedStationIds = emptySet(),
            historyRowsByStationId = emptyMap(),
            fetchedAt = now,
            cachePolicy = StationCachePolicy(),
            now = now,
        )

        assertEquals(listOf("ok"), result.stations.map { it.station.id })
    }

    @Test
    fun `toSearchResult returns every alteul source brand for the grouped filter`() {
        val result = listOf(
            cacheRow(stationId = "rto", latitude = 37.499095, longitude = 127.028610, brandCode = "RTO"),
            cacheRow(stationId = "rtx", latitude = 37.500095, longitude = 127.029610, brandCode = "RTX"),
            cacheRow(stationId = "nho", latitude = 37.501095, longitude = 127.030610, brandCode = "NHO"),
            cacheRow(stationId = "ske", latitude = 37.502095, longitude = 127.031610, brandCode = "SKE"),
        ).toSearchResult(
            query = query.copy(brandFilter = BrandFilter.ALTEUL),
            watchedStationIds = emptySet(),
            historyRowsByStationId = emptyMap(),
            fetchedAt = now,
            cachePolicy = StationCachePolicy(),
            now = now,
        )

        assertEquals(listOf("rto", "rtx", "nho"), result.stations.map { it.station.id })
    }

    private fun cacheRow(
        stationId: String,
        latitude: Double,
        longitude: Double,
        priceWon: Int = 1_680,
        brandCode: String = "GSC",
        name: String = "Station $stationId",
        fetchedAt: Instant = now,
    ) = StationCacheEntity(
        latitudeBucket = 0,
        longitudeBucket = 0,
        radiusMeters = 3_000,
        fuelType = "GASOLINE",
        stationId = stationId,
        brandCode = brandCode,
        name = name,
        priceWon = priceWon,
        latitude = latitude,
        longitude = longitude,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}
