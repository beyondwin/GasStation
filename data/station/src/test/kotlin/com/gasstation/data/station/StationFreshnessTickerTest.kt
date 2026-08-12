package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StationFreshnessTickerTest {
    private val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")

    @Test
    fun `observe emits fresh immediately then stale one millisecond after five minutes`() = runTest {
        val clock = MutableClock(fetchedAt)
        val emissions = mutableListOf<StationFreshness>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            StationFreshnessTicker(StationCachePolicy(), clock).observe(fetchedAt).toList(emissions)
        }

        assertEquals(listOf(StationFreshness.Fresh), emissions)

        clock.advance(Duration.ofMinutes(5))
        advanceTimeBy(Duration.ofMinutes(5).toMillis())
        runCurrent()
        assertEquals(listOf(StationFreshness.Fresh), emissions)

        clock.advance(Duration.ofMillis(1))
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(StationFreshness.Fresh, StationFreshness.Stale), emissions)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `observe emits stale immediately without scheduling a wait when already stale`() = runTest {
        val clock = MutableClock(fetchedAt.plus(Duration.ofMinutes(5)).plusMillis(1))
        val emissions = mutableListOf<StationFreshness>()

        StationFreshnessTicker(StationCachePolicy(), clock).observe(fetchedAt).toList(emissions)
        advanceUntilIdle()

        assertEquals(listOf(StationFreshness.Stale), emissions)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `cancelling collector removes pending freshness wait`() = runTest {
        val clock = MutableClock(fetchedAt)
        val emissions = mutableListOf<StationFreshness>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            StationFreshnessTicker(StationCachePolicy(), clock).observe(fetchedAt).toList(emissions)
        }

        assertEquals(listOf(StationFreshness.Fresh), emissions)
        job.cancelAndJoin()
        clock.advance(Duration.ofMinutes(5).plusMillis(1))
        advanceUntilIdle()

        assertEquals(listOf(StationFreshness.Fresh), emissions)
        assertEquals(0L, testScheduler.currentTime)
    }

    private class MutableClock(private var current: Instant, private val zoneId: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
