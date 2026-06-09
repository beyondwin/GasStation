package com.gasstation.data.station

import com.gasstation.domain.station.model.StationQuery
import java.util.Optional

class FlavorAwareStationRemoteDataSource(
    private val prodRemoteDataSource: StationRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedStationRemoteDataSource>,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        val seed = seedRemoteDataSource.orElse(null)
        return seed?.fetchStations(query) ?: prodRemoteDataSource.fetchStations(query)
    }
}
