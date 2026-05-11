package com.gasstation.analytics

import com.gasstation.domain.station.CrashReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, metadata: Map<String, String>) = Unit
    override fun log(message: String) = Unit
}
