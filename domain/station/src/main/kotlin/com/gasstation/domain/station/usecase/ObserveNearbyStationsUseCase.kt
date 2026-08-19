package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

public class ObserveNearbyStationsUseCase @Inject public constructor(private val stationRepository: StationRepository) {
    public operator fun invoke(query: StationQuery): Flow<StationSearchResult> = stationRepository.observeNearbyStations(query)
}
