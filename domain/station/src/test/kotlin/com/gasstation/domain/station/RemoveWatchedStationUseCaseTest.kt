package com.gasstation.domain.station

import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.RemoveWatchedStationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoveWatchedStationUseCaseTest {
    @Test
    fun `remove use case forwards the station id and repository outcome`() = runBlocking {
        val repository = RecordingStationRepository(WatchMutationResult.Superseded)

        val result = RemoveWatchedStationUseCase(repository)("station-42")

        assertEquals(WatchMutationResult.Superseded, result)
        assertEquals(listOf("station-42"), repository.removedStationIds)
    }

    private class RecordingStationRepository(
        private val removeResult: WatchMutationResult,
    ) : StationRepository {
        val removedStationIds = mutableListOf<String>()

        override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = emptyFlow()

        override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = emptyFlow()

        override suspend fun refreshNearbyStations(query: StationQuery) = Unit

        override suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult = WatchMutationResult.Committed

        override suspend fun removeWatchedStation(stationId: String): WatchMutationResult {
            removedStationIds += stationId
            return removeResult
        }
    }
}
