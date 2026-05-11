package com.gasstation.domain.station

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, metadata: Map<String, String> = emptyMap())
    fun log(message: String)
}
