package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject

class ObserveNearbyStationsUseCase @Inject constructor(
    private val stationRepository: StationRepository,
) {
    operator fun invoke(query: StationQuery) = stationRepository.observeNearbyStations(query)
}
