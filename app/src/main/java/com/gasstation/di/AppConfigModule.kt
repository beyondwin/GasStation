package com.gasstation.di

import com.gasstation.BuildConfig
import com.gasstation.core.network.di.NetworkRuntimeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}
