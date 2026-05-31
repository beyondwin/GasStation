package com.gasstation.di

import com.gasstation.BuildConfig
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.di.NetworkRuntimeConfig
import com.gasstation.core.network.di.StationEndpointMode
import com.gasstation.core.network.station.StationNetworkSource
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
    fun provideNetworkRuntimeConfig(): NetworkRuntimeConfig {
        val endpointMode = when (BuildConfig.STATION_ENDPOINT_MODE.lowercase()) {
            "proxy" -> StationEndpointMode.Proxy
            else -> StationEndpointMode.DirectOpinet
        }
        val stationBaseUrl = when (endpointMode) {
            StationEndpointMode.DirectOpinet -> NetworkModule.provideOpinetBaseUrl()
            StationEndpointMode.Proxy -> BuildConfig.PROXY_BASE_URL
        }
        return NetworkRuntimeConfig(
            opinetApiKey = BuildConfig.OPINET_API_KEY,
            stationEndpointMode = endpointMode,
            stationBaseUrl = stationBaseUrl,
        )
    }

    @Provides
    @Singleton
    fun provideStationNetworkSource(config: NetworkRuntimeConfig): StationNetworkSource = NetworkModule.provideStationNetworkSource(config)
}
