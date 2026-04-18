package com.gasstation.di

import com.gasstation.analytics.LogcatStationEventLogger
import com.gasstation.domain.station.StationEventLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun bindStationEventLogger(
        impl: LogcatStationEventLogger,
    ): StationEventLogger
}
