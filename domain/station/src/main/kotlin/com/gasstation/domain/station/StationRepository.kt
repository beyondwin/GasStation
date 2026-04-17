package com.gasstation.domain.station

import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult>

    suspend fun refreshNearbyStations(query: StationQuery)
}
