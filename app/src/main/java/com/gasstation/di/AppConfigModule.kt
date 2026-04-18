package com.gasstation.di

import com.gasstation.BuildConfig
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.di.NetworkRuntimeConfig
import com.gasstation.core.network.service.OpinetService
import com.gasstation.core.network.station.NetworkStationFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun provideNetworkRuntimeConfig(): NetworkRuntimeConfig = NetworkRuntimeConfig(
        kakaoApiKey = BuildConfig.KAKAO_API_KEY,
        opinetApiKey = BuildConfig.OPINET_API_KEY,
    )

    @Provides
    @Singleton
    @Named("opinetBaseUrl")
    fun provideOpinetBaseUrl(): String = NetworkModule.provideOpinetBaseUrl()

    @Provides
    @Singleton
    @Named("opinetApiKey")
    fun provideOpinetApiKey(config: NetworkRuntimeConfig): String = NetworkModule.provideOpinetApiKey(config)

    @Provides
    @Singleton
    fun provideOpinetService(
        @Named("opinetBaseUrl") baseUrl: String,
    ): OpinetService = NetworkModule.provideOpinetService(baseUrl)

    @Provides
    @Singleton
    fun provideNetworkStationFetcher(
        opinetService: OpinetService,
        @Named("opinetApiKey") opinetApiKey: String,
    ): NetworkStationFetcher = NetworkStationFetcher(
        opinetService = opinetService,
        opinetApiKey = opinetApiKey,
    )
}
