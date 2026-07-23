package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.WatchlistQuery
import javax.inject.Inject

class ObserveWatchlistUseCase @Inject constructor(private val stationRepository: StationRepository) {
    operator fun invoke(query: WatchlistQuery) = stationRepository.observeWatchlist(query)
}
