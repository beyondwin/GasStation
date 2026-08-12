package com.gasstation.domain.station

import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult>

    fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>>

    suspend fun refreshNearbyStations(query: StationQuery)

    suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult

    suspend fun removeWatchedStation(stationId: String): WatchMutationResult
}
