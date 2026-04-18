package com.gasstation.di

import com.gasstation.map.ExternalMapLauncher
import com.gasstation.map.IntentExternalMapLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExternalMapModule {
    @Provides
    @Singleton
    fun provideExternalMapLauncher(
        launcher: IntentExternalMapLauncher,
    ): ExternalMapLauncher = launcher
}
