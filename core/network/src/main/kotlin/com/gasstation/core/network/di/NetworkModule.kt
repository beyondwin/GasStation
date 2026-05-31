package com.gasstation.core.network.di

import com.gasstation.core.network.service.OpinetService
import com.gasstation.core.network.service.ProxyStationService
import com.gasstation.core.network.station.NetworkStationFetcher
import com.gasstation.core.network.station.ProxyStationFetcher
import com.gasstation.core.network.station.StationNetworkSource
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration

object NetworkModule {
    private const val OPINET_BASE_URL = "http://www.opinet.co.kr/"

    fun provideOpinetBaseUrl(): String = OPINET_BASE_URL

    fun provideOpinetApiKey(config: NetworkRuntimeConfig): String = config.opinetApiKey

    fun provideOpinetService(baseUrl: String): OpinetService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(defaultOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpinetService::class.java)

    fun provideProxyStationService(baseUrl: String): ProxyStationService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(defaultOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ProxyStationService::class.java)

    fun provideStationNetworkSource(config: NetworkRuntimeConfig): StationNetworkSource =
        when (config.stationEndpointMode) {
            StationEndpointMode.DirectOpinet -> NetworkStationFetcher(
                opinetService = provideOpinetService(provideOpinetBaseUrl()),
                opinetApiKey = config.opinetApiKey,
            )
            StationEndpointMode.Proxy -> ProxyStationFetcher(
                proxyStationService = provideProxyStationService(config.stationBaseUrl),
            )
        }

    private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(8))
        .connectTimeout(Duration.ofSeconds(4))
        .readTimeout(Duration.ofSeconds(8))
        .build()
}
