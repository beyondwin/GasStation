package com.gasstation.startup

import android.app.Application
import com.gasstation.core.network.di.NetworkRuntimeConfig
import com.gasstation.core.network.di.StationEndpointMode
import timber.log.Timber
import javax.inject.Inject

class ProdSecretsStartupHook @Inject constructor(private val networkConfig: NetworkRuntimeConfig) : AppStartupHook {
    override fun run(application: Application) {
        requireLocalSecrets()
        Timber.i("Prod network configuration validated for %s.", networkConfig.stationEndpointMode)
    }

    internal fun requireLocalSecrets() {
        check(networkConfig.stationEndpointMode != StationEndpointMode.DirectOpinet || networkConfig.opinetApiKey.isNotBlank()) {
            "Direct prod endpoint requires local secret: opinet.apikey."
        }
    }
}
