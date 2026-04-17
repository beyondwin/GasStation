package com.gasstation

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("TimberInitializer is initialized.")
        }
        maybeSeedDemoCache()
        maybeValidateProdSecrets()
    }

    private fun maybeSeedDemoCache() {
        if (!BuildConfig.DEMO_MODE) {
            return
        }
        invokeFlavorHook("com.gasstation.DemoSeedData") { type, instance ->
            type.getMethod("seed", Context::class.java).invoke(instance, this)
        }
    }

    private fun maybeValidateProdSecrets() {
        if (BuildConfig.DEMO_MODE) {
            return
        }
        invokeFlavorHook("com.gasstation.ProdSecretsModule") { type, instance ->
            type.getMethod(
                "warnIfMissing",
                String::class.java,
                String::class.java,
            ).invoke(instance, BuildConfig.OPINET_API_KEY, BuildConfig.KAKAO_API_KEY)
        }
    }

    private fun invokeFlavorHook(
        className: String,
        block: (Class<*>, Any) -> Unit,
    ) {
        runCatching {
            val type = Class.forName(className)
            val instance = checkNotNull(type.getField("INSTANCE").get(null))
            block(type, instance)
        }.onFailure { error ->
            if (error is ClassNotFoundException || error.cause is ClassNotFoundException) {
                return
            }
            throw error
        }
    }
}
