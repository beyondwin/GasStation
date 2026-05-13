package com.gasstation.data.station.mapper

import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.model.distanceTo
import com.gasstation.data.station.RemoteStation
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQueryCacheKey
import java.time.Instant

internal fun RemoteStation.toEntity(cacheKey: StationQueryCacheKey, fetchedAt: Instant): StationCacheEntity = StationCacheEntity(
    latitudeBucket = cacheKey.latitudeBucket,
    longitudeBucket = cacheKey.longitudeBucket,
    radiusMeters = cacheKey.radiusMeters,
    fuelType = cacheKey.fuelType.name,
    stationId = stationId,
    brandCode = brandCode,
    name = name,
    priceWon = priceWon,
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

internal fun StationCacheEntity.toDomainStation(queryCoordinates: Coordinates): Station = Station(
    id = stationId,
    name = name,
    brand = Brand.fromCode(brandCode),
    price = MoneyWon(priceWon),
    distance = queryCoordinates.distanceTo(Coordinates(latitude, longitude)),
    coordinates = Coordinates(latitude = latitude, longitude = longitude),
)
