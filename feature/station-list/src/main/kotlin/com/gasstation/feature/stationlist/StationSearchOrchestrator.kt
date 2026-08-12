package com.gasstation.feature.stationlist

import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class StationSearchOrchestrator @Inject constructor(private val observeNearbyStations: ObserveNearbyStationsUseCase) {
    private val mutableActiveQueryState = MutableStateFlow(ActiveStationQueryState())
    private val mutableSearchResult = MutableStateFlow(emptySearchResult())
    private val mutableBlockingFailure = MutableStateFlow<StationListFailureReason?>(null)
    private val pendingBlockingFailure = MutableStateFlow<PendingBlockingFailure?>(null)
    private val mutableObservationFailed = MutableStateFlow(false)
    private val retryGeneration = MutableStateFlow(0L)
    private val observationLock = Any()
    private var activeObservationSession: ObservationSession? = null

    val activeQueryState = mutableActiveQueryState.asStateFlow()
    val searchResult = mutableSearchResult.asStateFlow()
    val blockingFailure = mutableBlockingFailure.asStateFlow()
    val observationFailed = mutableObservationFailed.asStateFlow()

    fun observe(queryFlow: Flow<StationQuery?>): Flow<StationSearchResult> = queryFlow
        .distinctUntilChanged()
        .onEach(::onQueryChanged)
        .combine(retryGeneration) { query, generation ->
            ObservationSession(query, generation)
        }
        .distinctUntilChanged()
        .flatMapLatest { session ->
            onObservationSessionStarted(session)
            if (session.query == null) {
                flowOf(emptySearchResult())
                    .onEach { result -> onObservedResult(session, result) }
            } else {
                observeNearbyStations(session.query)
                    .onEach { result -> onObservedResult(session, result) }
                    .onCompletion { cause ->
                        if (cause == null) {
                            markObservationFailed(session)
                        }
                    }
                    .catch { throwable ->
                        if (throwable is CancellationException) throw throwable
                        markObservationFailed(session)
                    }
            }
        }

    fun retryObservation() {
        synchronized(observationLock) {
            val session = activeObservationSession
            if (!mutableObservationFailed.value || session?.query == null) return

            mutableObservationFailed.value = false
            retryGeneration.value += 1L
        }
    }

    fun ensureActiveQuery(query: StationQuery) {
        synchronized(observationLock) {
            if (mutableActiveQueryState.value.query == query) return
            onQueryChangedLocked(query)
        }
    }

    fun onRefreshSucceeded(query: StationQuery) {
        synchronized(observationLock) {
            if (mutableActiveQueryState.value.query != query) return
            clearBlockingFailureLocked()
        }
    }

    fun onRefreshFailure(query: StationQuery, reason: StationRefreshFailureReason?) {
        onBlockingFailure(
            query = query,
            reason = reason.toStationListFailureReason(),
        )
    }

    fun onBlockingFailure(query: StationQuery? = activeQueryState.value.query, reason: StationListFailureReason) {
        synchronized(observationLock) {
            if (query != null && activeQueryState.value.query != query) return

            when (activeQueryState.value.cacheState) {
                CachedSnapshotState.Present -> clearBlockingFailureLocked()

                CachedSnapshotState.Absent -> {
                    pendingBlockingFailure.value = null
                    mutableBlockingFailure.value = reason
                }

                CachedSnapshotState.Unknown -> {
                    pendingBlockingFailure.value = query?.let { PendingBlockingFailure(it, reason) }
                }
            }
        }
    }

    fun clearBlockingFailure() {
        synchronized(observationLock) {
            clearBlockingFailureLocked()
        }
    }

    private fun onQueryChanged(query: StationQuery?) {
        synchronized(observationLock) {
            onQueryChangedLocked(query)
        }
    }

    private fun onQueryChangedLocked(query: StationQuery?) {
        val previousQuery = mutableActiveQueryState.value.query
        mutableActiveQueryState.value = ActiveStationQueryState(
            query = query,
            cacheState = if (query == null) CachedSnapshotState.Absent else CachedSnapshotState.Unknown,
        )
        if (previousQuery != query) {
            activeObservationSession = null
            mutableSearchResult.value = emptySearchResult()
            mutableObservationFailed.value = false
            clearBlockingFailureLocked()
        }
    }

    private fun clearBlockingFailureLocked() {
        pendingBlockingFailure.value = null
        mutableBlockingFailure.value = null
    }

    private fun onObservationSessionStarted(session: ObservationSession) {
        synchronized(observationLock) {
            activeObservationSession = session
            mutableObservationFailed.value = false
        }
    }

    private fun onObservedResult(session: ObservationSession?, result: StationSearchResult) {
        synchronized(observationLock) {
            if (!isActive(session)) return

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
    }

    private fun markObservationFailed(session: ObservationSession) {
        synchronized(observationLock) {
            if (isActive(session)) {
                mutableObservationFailed.value = true
            }
        }
    }

    private fun isActive(session: ObservationSession?): Boolean = session != null &&
        activeObservationSession === session &&
        activeQueryState.value.query == session.query

    private fun syncBlockingFailureWithObservedResult(hasCachedSnapshot: Boolean) {
        if (hasCachedSnapshot) {
            clearBlockingFailureLocked()
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

private data class ObservationSession(val query: StationQuery?, val retryGeneration: Long)

data class ActiveStationQueryState(val query: StationQuery? = null, val cacheState: CachedSnapshotState = CachedSnapshotState.Absent)

private data class PendingBlockingFailure(val query: StationQuery, val reason: StationListFailureReason)

enum class CachedSnapshotState {
    Unknown,
    Present,
    Absent,
}

private fun StationRefreshFailureReason?.toStationListFailureReason(): StationListFailureReason = when (this) {
    StationRefreshFailureReason.Timeout -> StationListFailureReason.RefreshTimedOut

    StationRefreshFailureReason.Network,
    StationRefreshFailureReason.InvalidPayload,
    is StationRefreshFailureReason.Http,
    StationRefreshFailureReason.Unknown,
    null,
    -> StationListFailureReason.RefreshFailed
}

private fun emptySearchResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)
