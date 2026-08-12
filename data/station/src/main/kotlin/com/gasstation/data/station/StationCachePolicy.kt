package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import java.time.Duration
import java.time.Instant

class StationCachePolicy(private val staleAfter: Duration = Duration.ofMinutes(5), private val retainFor: Duration = Duration.ofDays(7)) {
    fun freshnessOf(fetchedAt: Instant, now: Instant): StationFreshness = if (Duration.between(fetchedAt, now) > staleAfter) {
        StationFreshness.Stale
    } else {
        StationFreshness.Fresh
    }

    fun staleBoundaryDelay(fetchedAt: Instant, now: Instant): Duration? {
        val staleAt = fetchedAt.plus(staleAfter).plusMillis(1)
        return Duration.between(now, staleAt).takeIf { !it.isNegative && !it.isZero }
    }

    fun pruneCutoff(now: Instant): Instant = now.minus(retainFor)
}
