package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

public class ObserveWatchlistUseCase @Inject public constructor(private val stationRepository: StationRepository) {
    public operator fun invoke(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = stationRepository.observeWatchlist(query)
}
