package com.gasstation.domain.station

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult>

    fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>>

    suspend fun refreshNearbyStations(query: StationQuery)

    suspend fun updateWatchState(station: Station, watched: Boolean)
}
