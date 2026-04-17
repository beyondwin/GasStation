package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject

class RefreshNearbyStationsUseCase @Inject constructor(
    private val stationRepository: StationRepository,
) {
    suspend operator fun invoke(query: StationQuery) {
        stationRepository.refreshNearbyStations(query)
    }
}
