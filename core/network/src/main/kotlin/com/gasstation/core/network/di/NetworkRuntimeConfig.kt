package com.gasstation.core.network.di

enum class StationEndpointMode {
    DirectOpinet,
    Proxy,
}

data class NetworkRuntimeConfig(
    val opinetApiKey: String,
    val stationEndpointMode: StationEndpointMode = StationEndpointMode.DirectOpinet,
    val stationBaseUrl: String = NetworkModule.provideOpinetBaseUrl(),
)
