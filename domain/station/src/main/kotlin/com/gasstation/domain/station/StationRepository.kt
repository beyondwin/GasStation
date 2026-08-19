package com.gasstation.domain.station

import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.flow.Flow

public interface StationRepository {
    public fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult>

    public fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>>

    public suspend fun refreshNearbyStations(query: StationQuery)

    public suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult

    public suspend fun removeWatchedStation(stationId: String): WatchMutationResult
}
