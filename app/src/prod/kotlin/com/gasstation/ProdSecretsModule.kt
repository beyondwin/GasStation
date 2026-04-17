package com.gasstation

import timber.log.Timber

object ProdSecretsModule {
    fun warnIfMissing(opinetApiKey: String, kakaoApiKey: String) {
        val missingSecrets = buildList {
            if (opinetApiKey.isBlank()) add("opinet.apikey")
            if (kakaoApiKey.isBlank()) add("kakao.apikey")
        }

        check(missingSecrets.isEmpty()) {
            "Prod flavor requires local secrets: ${missingSecrets.joinToString()}."
        }

        Timber.i("Prod secrets loaded from local Gradle properties.")
    }
}
