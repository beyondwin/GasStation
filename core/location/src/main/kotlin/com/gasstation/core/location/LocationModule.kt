package com.gasstation.core.location

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideForegroundLocationProvider(
        provider: AndroidForegroundLocationProvider,
    ): ForegroundLocationProvider = provider
}
