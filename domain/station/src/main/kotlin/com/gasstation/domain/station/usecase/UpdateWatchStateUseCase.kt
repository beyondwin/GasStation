package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import javax.inject.Inject

class UpdateWatchStateUseCase @Inject constructor(
    private val stationRepository: StationRepository,
) {
    suspend operator fun invoke(
        station: Station,
        watched: Boolean,
    ) {
        stationRepository.updateWatchState(station, watched)
    }
}
