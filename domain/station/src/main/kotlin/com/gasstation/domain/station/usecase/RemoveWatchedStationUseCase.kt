package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.WatchMutationResult
import javax.inject.Inject

class RemoveWatchedStationUseCase @Inject constructor(private val stationRepository: StationRepository) {
    suspend operator fun invoke(stationId: String): WatchMutationResult = stationRepository.removeWatchedStation(stationId)
}
