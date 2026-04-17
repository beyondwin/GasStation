package com.gasstation.di

import android.content.Context
import com.gasstation.core.location.ForegroundLocationProvider
import com.gasstation.location.AndroidForegroundLocationProvider
import com.gasstation.map.ExternalMapLauncher
import com.gasstation.map.IntentExternalMapLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideForegroundLocationProvider(
        @ApplicationContext context: Context,
    ): ForegroundLocationProvider = AndroidForegroundLocationProvider(context)

    @Provides
    @Singleton
    fun provideExternalMapLauncher(
        launcher: IntentExternalMapLauncher,
    ): ExternalMapLauncher = launcher
}
