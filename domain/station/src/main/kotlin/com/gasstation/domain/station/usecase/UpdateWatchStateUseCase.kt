package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.WatchMutationResult
import javax.inject.Inject

public class UpdateWatchStateUseCase @Inject public constructor(private val stationRepository: StationRepository) {
    public suspend operator fun invoke(station: Station, watched: Boolean): WatchMutationResult =
        stationRepository.updateWatchState(station, watched)
}
