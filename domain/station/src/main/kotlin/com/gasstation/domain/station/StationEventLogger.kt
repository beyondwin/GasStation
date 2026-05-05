package com.gasstation.domain.station

import com.gasstation.domain.station.model.StationEvent
import kotlinx.coroutines.CancellationException

interface StationEventLogger {
    fun log(event: StationEvent)
}

fun StationEventLogger.logSafely(event: StationEvent) {
    try {
        log(event)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        // Analytics must not turn successful user or data flows into failures.
    }
}
