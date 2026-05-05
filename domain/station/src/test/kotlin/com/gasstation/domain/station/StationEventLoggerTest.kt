package com.gasstation.domain.station

import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.model.StationEvent
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StationEventLoggerTest {
    @Test
    fun `logSafely swallows ordinary runtime exception from analytics logger`() {
        ThrowingStationEventLogger(IllegalStateException("analytics failed")).logSafely(testEvent)
    }

    @Test
    fun `logSafely swallows ordinary checked exception from analytics logger`() {
        ThrowingStationEventLogger(Exception("analytics failed")).logSafely(testEvent)
    }

    @Test
    fun `logSafely rethrows cancellation from analytics logger`() {
        assertFailsWith<CancellationException> {
            ThrowingStationEventLogger(CancellationException("cancelled")).logSafely(testEvent)
        }
    }

    @Test
    fun `logSafely does not swallow errors from analytics logger`() {
        assertFailsWith<AssertionError> {
            ThrowingStationEventLogger(AssertionError("fatal")).logSafely(testEvent)
        }
    }

    private class ThrowingStationEventLogger(
        private val throwable: Throwable,
    ) : StationEventLogger {
        override fun log(event: StationEvent) {
            throw throwable
        }
    }

    private companion object {
        val testEvent = StationEvent.SearchRefreshed(
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            sortOrder = SortOrder.DISTANCE,
            stale = false,
        )
    }
}
