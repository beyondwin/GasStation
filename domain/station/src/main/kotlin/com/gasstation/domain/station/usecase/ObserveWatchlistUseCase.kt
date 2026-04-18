package com.gasstation.domain.station.usecase

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.StationRepository
import javax.inject.Inject

class ObserveWatchlistUseCase @Inject constructor(
    private val stationRepository: StationRepository,
) {
    operator fun invoke(origin: Coordinates) = stationRepository.observeWatchlist(origin)
}
