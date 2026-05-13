package com.gasstation.core.observability

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, metadata: Map<String, String> = emptyMap())
    fun log(message: String)
}
