package com.gasstation.analytics

import com.gasstation.core.observability.CrashReporter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogcatCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) {
        val metaString = metadata.entries.joinToString(separator = " ") { (k, v) -> "$k=$v" }
        Timber.tag(TAG).e(throwable, "non-fatal $metaString")
    }

    override fun log(message: String) {
        Timber.tag(TAG).i(message)
    }

    private companion object {
        const val TAG = "GasStationCrash"
    }
}
