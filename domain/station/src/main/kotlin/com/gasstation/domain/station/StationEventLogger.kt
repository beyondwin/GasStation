package com.gasstation.domain.station

import com.gasstation.domain.station.model.StationEvent
import kotlinx.coroutines.CancellationException

public interface StationEventLogger {
    public fun log(event: StationEvent)
}

public fun StationEventLogger.logSafely(event: StationEvent) {
    try {
        log(event)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        // Analytics must not turn successful user or data flows into failures.
    }
}
