package com.gasstation.data.station

import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.logSafely
import com.gasstation.domain.station.model.StationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject

class StationRetryPolicy @Inject constructor(private val stationEventLogger: StationEventLogger) {
    suspend fun <T> withRetry(block: suspend () -> T): T = when (val result = execute(block)) {
        is RetryExecutionResult.Success -> {
            result.retryReason?.let { retryReason ->
                stationEventLogger.logSafely(
                    StationEvent.RetryAttempted(
                        originalReason = retryReason,
                        succeeded = true,
                    ),
                )
            }
            result.value
        }

        is RetryExecutionResult.Failure -> {
            result.retryReason?.let { retryReason ->
                stationEventLogger.logSafely(
                    StationEvent.RetryAttempted(
                        originalReason = retryReason,
                        succeeded = false,
                    ),
                )
            }
            throw result.exception
        }
    }

    internal suspend fun <T> execute(block: suspend () -> T): RetryExecutionResult<T> = try {
        RetryExecutionResult.Success(value = block(), retryReason = null)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (exception: StationRefreshException) {
        if (!exception.reason.isRetryable()) {
            RetryExecutionResult.Failure(exception = exception, retryReason = null)
        } else {
            delay(RETRY_DELAY_MS)
            try {
                RetryExecutionResult.Success(value = block(), retryReason = exception.reason)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (retryException: StationRefreshException) {
                RetryExecutionResult.Failure(
                    exception = retryException,
                    retryReason = exception.reason,
                )
            }
        }
    }

    private fun StationRefreshFailureReason.isRetryable(): Boolean = when (this) {
        StationRefreshFailureReason.Timeout,
        StationRefreshFailureReason.Network,
        -> true

        StationRefreshFailureReason.InvalidPayload,
        StationRefreshFailureReason.Unknown,
        -> false

        is StationRefreshFailureReason.Http ->
            statusCode == 408 || statusCode == 429 || statusCode in 500..599
    }

    companion object {
        const val RETRY_DELAY_MS = 500L
    }
}

internal sealed interface RetryExecutionResult<out T> {
    data class Success<T>(val value: T, val retryReason: StationRefreshFailureReason?) : RetryExecutionResult<T>

    data class Failure(val exception: StationRefreshException, val retryReason: StationRefreshFailureReason?) :
        RetryExecutionResult<Nothing>
}
