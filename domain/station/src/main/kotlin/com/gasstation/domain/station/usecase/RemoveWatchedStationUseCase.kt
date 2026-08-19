package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.WatchMutationResult
import javax.inject.Inject

public class RemoveWatchedStationUseCase @Inject public constructor(private val stationRepository: StationRepository) {
    public suspend operator fun invoke(stationId: String): WatchMutationResult = stationRepository.removeWatchedStation(stationId)
}
