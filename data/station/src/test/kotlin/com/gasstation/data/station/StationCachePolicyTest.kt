package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StationCachePolicyTest {

    @Test
    fun `result at exact five minute boundary remains fresh`() {
        val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")
        val now = Instant.parse("2026-04-18T03:05:00Z")

        assertEquals(
            StationFreshness.Fresh,
            StationCachePolicy().freshnessOf(
                fetchedAt = fetchedAt,
                now = now,
            ),
        )
    }

    @Test
    fun `result after five minute boundary becomes stale`() {
        val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")
        val now = Instant.parse("2026-04-18T03:05:00.001Z")

        assertEquals(
            StationFreshness.Stale,
            StationCachePolicy().freshnessOf(
                fetchedAt = fetchedAt,
                now = now,
            ),
        )
    }

    @Test
    fun `fresh result delays until one millisecond after five minute boundary`() {
        val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")
        val now = Instant.parse("2026-04-18T03:05:00Z")

        assertEquals(
            Duration.ofMillis(1),
            StationCachePolicy().staleBoundaryDelay(
                fetchedAt = fetchedAt,
                now = now,
            ),
        )
    }

    @Test
    fun `already stale result has no boundary delay`() {
        val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")
        val now = Instant.parse("2026-04-18T03:05:00.001Z")

        assertNull(
            StationCachePolicy().staleBoundaryDelay(
                fetchedAt = fetchedAt,
                now = now,
            ),
        )
    }
}
