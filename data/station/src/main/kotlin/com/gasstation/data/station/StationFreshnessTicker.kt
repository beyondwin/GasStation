package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class StationFreshnessTicker @Inject constructor(private val cachePolicy: StationCachePolicy, private val clock: Clock) {
    fun observe(fetchedAt: Instant): Flow<StationFreshness> = flow {
        val now = clock.instant()
        val freshness = cachePolicy.freshnessOf(fetchedAt, now)
        emit(freshness)
        if (freshness is StationFreshness.Fresh) {
            cachePolicy.staleBoundaryDelay(fetchedAt, now)?.let { boundaryDelay ->
                delay(boundaryDelay.toMillis())
                emit(StationFreshness.Stale)
            }
        }
    }
}
