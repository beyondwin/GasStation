package com.gasstation.startup

import android.app.Application
import com.gasstation.BuildConfig
import timber.log.Timber
import javax.inject.Inject

class ProdSecretsStartupHook @Inject constructor() : AppStartupHook {
    override fun run(application: Application) {
        requireLocalSecrets(opinetApiKey = BuildConfig.OPINET_API_KEY)
        Timber.i("Prod secrets loaded from local Gradle properties.")
    }

    internal fun requireLocalSecrets(opinetApiKey: String) {
        val missingSecrets = buildList {
            if (opinetApiKey.isBlank()) add("opinet.apikey")
        }

        check(missingSecrets.isEmpty()) {
            "Prod flavor requires local secrets: ${missingSecrets.joinToString()}."
        }
    }
}
