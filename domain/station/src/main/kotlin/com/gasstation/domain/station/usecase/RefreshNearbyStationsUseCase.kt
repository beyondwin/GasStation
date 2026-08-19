package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject

public class RefreshNearbyStationsUseCase @Inject public constructor(private val stationRepository: StationRepository) {
    public suspend operator fun invoke(query: StationQuery) {
        stationRepository.refreshNearbyStations(query)
    }
}
