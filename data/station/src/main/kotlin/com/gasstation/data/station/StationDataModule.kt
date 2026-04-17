package com.gasstation.data.station

import com.gasstation.domain.station.StationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StationDataModule {

    @Binds
    @Singleton
    abstract fun bindStationRepository(
        repository: DefaultStationRepository,
    ): StationRepository

    @Binds
    @Singleton
    abstract fun bindStationRemoteDataSource(
        remoteDataSource: DefaultStationRemoteDataSource,
    ): StationRemoteDataSource

    companion object {
        @Provides
        @Singleton
        fun provideStationCachePolicy(): StationCachePolicy = StationCachePolicy()

        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
