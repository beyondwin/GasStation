package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import org.junit.Assert.assertEquals
import org.junit.Test
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
        val now = Instant.parse("2026-04-18T03:05:01Z")

        assertEquals(
            StationFreshness.Stale,
            StationCachePolicy().freshnessOf(
                fetchedAt = fetchedAt,
                now = now,
            ),
        )
    }
}
