package com.gasstation.data.station

import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StationRetryPolicyTest {

    private val logger = RecordingStationEventLogger()
    private val policy = StationRetryPolicy(stationEventLogger = logger)

    @Test
    fun `success on first attempt returns result without retry event`() = runTest {
        var callCount = 0

        val result = policy.withRetry {
            callCount += 1
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `timeout failure retries once and succeeds`() = runTest {
        var callCount = 0

        val result = policy.withRetry {
            callCount += 1
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, callCount)
        val event = assertIs<StationEvent.RetryAttempted>(logger.events.single())
        assertEquals(StationRefreshFailureReason.Timeout, event.originalReason)
        assertEquals(true, event.succeeded)
    }

    @Test
    fun `network failure retries once and succeeds`() = runTest {
        var callCount = 0

        val result = policy.withRetry {
            callCount += 1
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Network)
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, callCount)
        val event = assertIs<StationEvent.RetryAttempted>(logger.events.single())
        assertEquals(StationRefreshFailureReason.Network, event.originalReason)
        assertEquals(true, event.succeeded)
    }

    @Test
    fun `successful retry returns result when retry success logging fails`() = runTest {
        val throwingPolicy = StationRetryPolicy(stationEventLogger = ThrowingStationEventLogger())
        var callCount = 0

        val result = throwingPolicy.withRetry {
            callCount += 1
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Network)
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryable failure retries once then propagates second failure`() = runTest {
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            policy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
        }

        assertEquals(StationRefreshFailureReason.Timeout, exception.reason)
        assertEquals(2, callCount)
        val event = assertIs<StationEvent.RetryAttempted>(logger.events.single())
        assertEquals(StationRefreshFailureReason.Timeout, event.originalReason)
        assertEquals(false, event.succeeded)
    }

    @Test
    fun `failed retry propagates original station refresh exception when retry failure logging fails`() = runTest {
        val throwingPolicy = StationRetryPolicy(stationEventLogger = ThrowingStationEventLogger())
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            throwingPolicy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.Timeout)
            }
        }

        assertEquals(StationRefreshFailureReason.Timeout, exception.reason)
        assertEquals(2, callCount)
    }

    @Test
    fun `unexpected retry exception propagates without failed retry event`() = runTest {
        var callCount = 0

        assertFailsWith<IllegalStateException> {
            policy.withRetry {
                callCount += 1
                if (callCount == 1) {
                    throw StationRefreshException(StationRefreshFailureReason.Network)
                }
                throw IllegalStateException("unexpected")
            }
        }

        assertEquals(2, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `invalid payload does not retry`() = runTest {
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            policy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.InvalidPayload)
            }
        }

        assertEquals(StationRefreshFailureReason.InvalidPayload, exception.reason)
        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `unknown failure does not retry`() = runTest {
        var callCount = 0

        val exception = assertFailsWith<StationRefreshException> {
            policy.withRetry {
                callCount += 1
                throw StationRefreshException(StationRefreshFailureReason.Unknown)
            }
        }

        assertEquals(StationRefreshFailureReason.Unknown, exception.reason)
        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `cancellation is never retried`() = runTest {
        var callCount = 0

        assertFailsWith<CancellationException> {
            policy.withRetry {
                callCount += 1
                throw CancellationException("cancelled")
            }
        }

        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `non station refresh exception is not retried`() = runTest {
        var callCount = 0

        assertFailsWith<IllegalStateException> {
            policy.withRetry {
                callCount += 1
                throw IllegalStateException("unexpected")
            }
        }

        assertEquals(1, callCount)
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun `retry waits 500ms before second attempt`() = runTest {
        var callCount = 0
        val attemptTimes = mutableListOf<Long>()

        policy.withRetry {
            callCount += 1
            attemptTimes += testScheduler.currentTime
            if (callCount == 1) {
                throw StationRefreshException(StationRefreshFailureReason.Network)
            }
            "ok"
        }

        assertEquals(listOf(0L, 500L), attemptTimes)
    }
}

private class RecordingStationEventLogger : StationEventLogger {
    val events = mutableListOf<StationEvent>()

    override fun log(event: StationEvent) {
        events += event
    }
}

private class ThrowingStationEventLogger : StationEventLogger {
    override fun log(event: StationEvent) {
        throw IllegalStateException("analytics failed")
    }
}
