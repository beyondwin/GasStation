package com.gasstation.core.observability

import java.util.concurrent.CancellationException

public interface CrashReporter {
    public fun recordNonFatal(throwable: Throwable, metadata: Map<String, String> = emptyMap())
    public fun log(message: String)
}

public fun CrashReporter.recordNonFatalSafely(throwable: Throwable, metadata: Map<String, String> = emptyMap()) {
    try {
        recordNonFatal(throwable, metadata)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // Diagnostics must not replace the recoverable failure being recorded.
    }
}
