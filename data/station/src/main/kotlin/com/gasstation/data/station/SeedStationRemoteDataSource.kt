package com.gasstation.data.station

import com.gasstation.domain.station.model.StationQuery
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface SeedStationRemoteDataSource {
    suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SeedStationRemoteDataSourceModule {
    @BindsOptionalOf
    abstract fun bindSeedStationRemoteDataSource(): SeedStationRemoteDataSource
}
