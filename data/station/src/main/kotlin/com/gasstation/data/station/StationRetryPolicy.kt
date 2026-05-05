package com.gasstation.data.station

import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.StationEvent
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class StationRetryPolicy @Inject constructor(
    private val stationEventLogger: StationEventLogger,
) {
    suspend fun <T> withRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: StationRefreshException) {
            if (!exception.reason.isRetryable()) {
                throw exception
            }

            delay(RETRY_DELAY_MS)
            try {
                val result = block()
                stationEventLogger.logSafely(
                    StationEvent.RetryAttempted(
                        originalReason = exception.reason,
                        succeeded = true,
                    ),
                )
                result
            } catch (retryException: StationRefreshException) {
                stationEventLogger.logSafely(
                    StationEvent.RetryAttempted(
                        originalReason = exception.reason,
                        succeeded = false,
                    ),
                )
                throw retryException
            }
        }
    }

    private fun StationRefreshFailureReason.isRetryable(): Boolean = when (this) {
        StationRefreshFailureReason.Timeout,
        StationRefreshFailureReason.Network -> true
        StationRefreshFailureReason.InvalidPayload,
        StationRefreshFailureReason.Unknown -> false
    }

    companion object {
        const val RETRY_DELAY_MS = 500L
    }
}
