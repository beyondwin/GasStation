package com.gasstation.feature.stationlist

import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class StationSearchOrchestrator @Inject constructor(
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
) {
    private val mutableActiveQueryState = MutableStateFlow(ActiveStationQueryState())
    private val mutableSearchResult = MutableStateFlow(emptySearchResult())
    private val mutableBlockingFailure = MutableStateFlow<StationListFailureReason?>(null)
    private val pendingBlockingFailure = MutableStateFlow<PendingBlockingFailure?>(null)

    val activeQueryState = mutableActiveQueryState.asStateFlow()
    val searchResult = mutableSearchResult.asStateFlow()
    val blockingFailure = mutableBlockingFailure.asStateFlow()

    fun observe(queryFlow: Flow<StationQuery?>): Flow<StationSearchResult> =
        queryFlow.distinctUntilChanged()
            .onEach(::onQueryChanged)
            .flatMapLatest { query ->
                if (query == null) {
                    flowOf(emptySearchResult())
                } else {
                    observeNearbyStations(query)
                }
            }
            .onEach(::onObservedResult)

    suspend fun refresh(query: StationQuery): RefreshOutcome {
        if (activeQueryState.value.query != query) {
            onQueryChanged(query)
        }
        return try {
            refreshNearbyStations(query)
            if (activeQueryState.value.query == query) {
                clearBlockingFailure()
            }
            RefreshOutcome.Success
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            RefreshOutcome.Failed((throwable as? StationRefreshException)?.reason)
        }
    }

    fun onRefreshFailure(query: StationQuery, reason: StationRefreshFailureReason?) {
        onBlockingFailure(
            query = query,
            reason = reason.toStationListFailureReason(),
        )
    }

    fun onBlockingFailure(
        query: StationQuery? = activeQueryState.value.query,
        reason: StationListFailureReason,
    ) {
        if (query != null && activeQueryState.value.query != query) return

        when (activeQueryState.value.cacheState) {
            CachedSnapshotState.Present -> clearBlockingFailure()
            CachedSnapshotState.Absent -> {
                pendingBlockingFailure.value = null
                mutableBlockingFailure.value = reason
            }

            CachedSnapshotState.Unknown -> {
                pendingBlockingFailure.value = query?.let { PendingBlockingFailure(it, reason) }
            }
        }
    }

    fun clearBlockingFailure() {
        pendingBlockingFailure.value = null
        mutableBlockingFailure.value = null
    }

    fun shouldRefreshForCriteriaChange(previous: StationQuery?, next: StationQuery?): Boolean =
        previous != null &&
            next != null &&
            previous.coordinates == next.coordinates &&
            (
                previous.radius != next.radius ||
                    previous.fuelType != next.fuelType ||
                    previous.brandFilter != next.brandFilter ||
                    previous.sortOrder != next.sortOrder
                )

    private fun onQueryChanged(query: StationQuery?) {
        val previousQuery = activeQueryState.value.query
        mutableActiveQueryState.value = ActiveStationQueryState(
            query = query,
            cacheState = if (query == null) CachedSnapshotState.Absent else CachedSnapshotState.Unknown,
        )
        if (previousQuery != query) {
            clearBlockingFailure()
        }
    }

    private fun onObservedResult(result: StationSearchResult) {
        mutableSearchResult.value = result
        val hasCachedSnapshot = result.hasCachedSnapshot
        mutableActiveQueryState.update { current ->
            current.copy(
                cacheState = if (hasCachedSnapshot) {
                    CachedSnapshotState.Present
                } else {
                    CachedSnapshotState.Absent
                },
            )
        }
        syncBlockingFailureWithObservedResult(hasCachedSnapshot)
    }

    private fun syncBlockingFailureWithObservedResult(hasCachedSnapshot: Boolean) {
        if (hasCachedSnapshot) {
            clearBlockingFailure()
            return
        }

        val activeQuery = activeQueryState.value.query ?: return
        val pendingFailure = pendingBlockingFailure.value
            ?.takeIf { it.query == activeQuery }
            ?: return
        pendingBlockingFailure.value = null
        mutableBlockingFailure.value = pendingFailure.reason
    }
}

data class ActiveStationQueryState(
    val query: StationQuery? = null,
    val cacheState: CachedSnapshotState = CachedSnapshotState.Absent,
)

private data class PendingBlockingFailure(
    val query: StationQuery,
    val reason: StationListFailureReason,
)

enum class CachedSnapshotState {
    Unknown,
    Present,
    Absent,
}

sealed interface RefreshOutcome {
    data object Success : RefreshOutcome
    data class Failed(val reason: StationRefreshFailureReason?) : RefreshOutcome
}

private fun StationRefreshFailureReason?.toStationListFailureReason(): StationListFailureReason = when (this) {
    StationRefreshFailureReason.Timeout -> StationListFailureReason.RefreshTimedOut
    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    StationRefreshFailureReason.Unknown,
    null -> StationListFailureReason.RefreshFailed
}

private fun emptySearchResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)
