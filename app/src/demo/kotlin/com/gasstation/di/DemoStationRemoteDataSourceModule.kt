package com.gasstation.di

import com.gasstation.data.station.SeedStationRemoteDataSource
import com.gasstation.demo.seed.DemoSeedStationRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoStationRemoteDataSourceModule {
    @Binds
    @Singleton
    abstract fun bindSeedStationRemoteDataSource(
        dataSource: DemoSeedStationRemoteDataSource,
    ): SeedStationRemoteDataSource
}
