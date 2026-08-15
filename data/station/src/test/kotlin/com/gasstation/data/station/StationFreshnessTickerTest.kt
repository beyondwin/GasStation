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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StationFreshnessTickerTest {
    private val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")

    @Test
    fun `observe emits fresh immediately then stale one millisecond after five minutes`() = runTest {
        val clock = Clock.fixed(fetchedAt, ZoneOffset.UTC)
        val emissions = mutableListOf<StationFreshness>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            StationFreshnessTicker(StationCachePolicy(), clock).observe(fetchedAt).toList(emissions)
        }

        assertEquals(listOf(StationFreshness.Fresh), emissions)

        advanceTimeBy(Duration.ofMinutes(5).toMillis())
        runCurrent()
        assertEquals(listOf(StationFreshness.Fresh), emissions)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(StationFreshness.Fresh, StationFreshness.Stale), emissions)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `observe emits stale immediately without scheduling a wait when already stale`() = runTest {
        val clock = Clock.fixed(
            fetchedAt.plus(Duration.ofMinutes(5)).plusMillis(1),
            ZoneOffset.UTC,
        )
        val emissions = mutableListOf<StationFreshness>()

        StationFreshnessTicker(StationCachePolicy(), clock).observe(fetchedAt).toList(emissions)
        advanceUntilIdle()

        assertEquals(listOf(StationFreshness.Stale), emissions)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `cancelling collector removes pending freshness wait`() = runTest {
        val clock = Clock.fixed(fetchedAt, ZoneOffset.UTC)
        val emissions = mutableListOf<StationFreshness>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            StationFreshnessTicker(StationCachePolicy(), clock).observe(fetchedAt).toList(emissions)
        }

        assertEquals(listOf(StationFreshness.Fresh), emissions)
        job.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(listOf(StationFreshness.Fresh), emissions)
        assertEquals(0L, testScheduler.currentTime)
    }
}
