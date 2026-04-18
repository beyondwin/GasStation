package com.gasstation.startup

import android.app.Application
import com.gasstation.BuildConfig
import javax.inject.Inject
import timber.log.Timber

class ProdSecretsStartupHook @Inject constructor() : AppStartupHook {
    override fun run(application: Application) {
        val missingSecrets = buildList {
            if (BuildConfig.OPINET_API_KEY.isBlank()) add("opinet.apikey")
            if (BuildConfig.KAKAO_API_KEY.isBlank()) add("kakao.apikey")
        }

        check(missingSecrets.isEmpty()) {
            "Prod flavor requires local secrets: ${missingSecrets.joinToString()}."
        }

        Timber.i("Prod secrets loaded from local Gradle properties.")
    }
}
