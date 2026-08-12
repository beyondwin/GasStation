package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RefreshCoordinatorState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val activeQuery: StationQuery? = null,
)

sealed interface RefreshRequest {
    data class AcquireLocation(val showPermissionDeniedFeedback: Boolean) : RefreshRequest

    data class ActiveQuery(val query: StationQuery) : RefreshRequest
}

sealed interface RefreshCoordinatorResult {
    data class PermissionRequired(val showFeedback: Boolean) : RefreshCoordinatorResult

    data object GpsDisabled : RefreshCoordinatorResult

    data class LocationAcquired(val coordinates: Coordinates) : RefreshCoordinatorResult

    data class LocationAcquisitionFailed(val result: LocationAcquisitionResult, val showPermissionDeniedFeedback: Boolean) :
        RefreshCoordinatorResult

    data class RefreshStarting(val query: StationQuery) : RefreshCoordinatorResult

    data class RefreshSucceeded(val query: StationQuery) : RefreshCoordinatorResult

    data class RefreshFailed(val query: StationQuery, val reason: StationRefreshFailureReason?) : RefreshCoordinatorResult
}

@ViewModelScoped
class RefreshCoordinator @Inject constructor(
    private val locationStateMachine: LocationStateMachine,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
) {
    private val workLock = Any()
    private val mutableState = MutableStateFlow(RefreshCoordinatorState())
    private var nextWorkId = 1L
    private var activeWork: ActiveRefreshWork? = null

    val state: StateFlow<RefreshCoordinatorState> = mutableState.asStateFlow()

    fun request(
        scope: CoroutineScope,
        request: RefreshRequest,
        latestEligibleQuery: () -> StationQuery?,
        onResult: suspend (RefreshCoordinatorResult) -> Unit,
    ) {
        lateinit var work: ActiveRefreshWork
        val job = scope.launch(start = CoroutineStart.LAZY) {
            when (request) {
                is RefreshRequest.AcquireLocation -> executeLocationRequest(
                    work = work,
                    request = request,
                    latestEligibleQuery = latestEligibleQuery,
                    onResult = onResult,
                )

                is RefreshRequest.ActiveQuery -> executeActiveQueryRequest(
                    work = work,
                    query = request.query,
                    latestEligibleQuery = latestEligibleQuery,
                    onResult = onResult,
                )
            }
        }

        synchronized(workLock) {
            check(nextWorkId > 0 && nextWorkId < Long.MAX_VALUE) { "Refresh work id exhausted" }
            val workId = nextWorkId++
            activeWork?.job?.cancel()
            work = ActiveRefreshWork(id = workId, job = job)
            mutableState.value = initialState(request)
            activeWork = work
            job.invokeOnCompletion { finishIfActive(work) }
        }
        job.start()
    }

    fun cancel() {
        val job = synchronized(workLock) {
            val current = activeWork ?: return
            activeWork = null
            mutableState.value = RefreshCoordinatorState()
            current.job
        }
        job.cancel()
    }

    fun requiresRefresh(previous: StationQuery?, next: StationQuery?): Boolean = previous != null &&
        next != null &&
        previous.coordinates == next.coordinates &&
        (
            previous.radius != next.radius ||
                previous.fuelType != next.fuelType ||
                previous.brandFilter != next.brandFilter ||
                previous.sortOrder != next.sortOrder
            )

    private suspend fun executeLocationRequest(
        work: ActiveRefreshWork,
        request: RefreshRequest.AcquireLocation,
        latestEligibleQuery: () -> StationQuery?,
        onResult: suspend (RefreshCoordinatorResult) -> Unit,
    ) {
        val location = locationStateMachine.state.value
        if (location.permissionState == LocationPermissionState.Denied) {
            deliver(
                work,
                RefreshCoordinatorResult.PermissionRequired(request.showPermissionDeniedFeedback),
                onResult,
            )
            return
        }
        if (!location.isGpsEnabled) {
            deliver(work, RefreshCoordinatorResult.GpsDisabled, onResult)
            return
        }

        val acquisitionResult = locationStateMachine.acquireLocation()
        if (!ensureCurrent(work)) return
        val coordinates = when (acquisitionResult) {
            is LocationAcquisitionResult.Success -> acquisitionResult.coordinates

            LocationAcquisitionResult.Superseded -> return

            LocationAcquisitionResult.PermissionDenied,
            LocationAcquisitionResult.TimedOut,
            LocationAcquisitionResult.Unavailable,
            is LocationAcquisitionResult.Error,
            -> {
                deliver(
                    work,
                    RefreshCoordinatorResult.LocationAcquisitionFailed(
                        result = acquisitionResult,
                        showPermissionDeniedFeedback = request.showPermissionDeniedFeedback,
                    ),
                    onResult,
                )
                return
            }
        }

        if (!deliver(work, RefreshCoordinatorResult.LocationAcquired(coordinates), onResult)) return
        val query = latestEligibleQuery()?.takeIf { it.coordinates == coordinates } ?: return
        if (!publishActiveQuery(work, query)) return
        executeRefresh(work, query, latestEligibleQuery, onResult)
    }

    private suspend fun executeActiveQueryRequest(
        work: ActiveRefreshWork,
        query: StationQuery,
        latestEligibleQuery: () -> StationQuery?,
        onResult: suspend (RefreshCoordinatorResult) -> Unit,
    ) {
        if (!ensureCurrent(work) || latestEligibleQuery() != query) return
        executeRefresh(work, query, latestEligibleQuery, onResult)
    }

    private suspend fun executeRefresh(
        work: ActiveRefreshWork,
        query: StationQuery,
        latestEligibleQuery: () -> StationQuery?,
        onResult: suspend (RefreshCoordinatorResult) -> Unit,
    ) {
        if (!ensureCurrent(work) || latestEligibleQuery() != query) return
        if (!deliver(work, RefreshCoordinatorResult.RefreshStarting(query), onResult)) return
        if (!ensureCurrent(work) || latestEligibleQuery() != query) return

        val outcome = try {
            refreshNearbyStations(query)
            RefreshExecutionResult.Succeeded
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            RefreshExecutionResult.Failed((exception as? StationRefreshException)?.reason)
        }

        if (!ensureCurrent(work) || latestEligibleQuery() != query) return
        when (outcome) {
            RefreshExecutionResult.Succeeded -> deliver(
                work,
                RefreshCoordinatorResult.RefreshSucceeded(query),
                onResult,
            )

            is RefreshExecutionResult.Failed -> deliver(
                work,
                RefreshCoordinatorResult.RefreshFailed(query, outcome.reason),
                onResult,
            )
        }
    }

    private suspend fun deliver(
        work: ActiveRefreshWork,
        result: RefreshCoordinatorResult,
        onResult: suspend (RefreshCoordinatorResult) -> Unit,
    ): Boolean {
        if (!ensureCurrent(work)) return false
        onResult(result)
        return ensureCurrent(work)
    }

    private suspend fun ensureCurrent(work: ActiveRefreshWork): Boolean {
        currentCoroutineContext().ensureActive()
        return synchronized(workLock) { activeWork === work }
    }

    private fun publishActiveQuery(work: ActiveRefreshWork, query: StationQuery): Boolean = synchronized(workLock) {
        if (activeWork !== work) return@synchronized false
        mutableState.value = RefreshCoordinatorState(
            isLoading = true,
            isRefreshing = true,
            activeQuery = query,
        )
        true
    }

    private fun finishIfActive(work: ActiveRefreshWork) {
        synchronized(workLock) {
            if (activeWork !== work) return
            activeWork = null
            mutableState.value = RefreshCoordinatorState()
        }
    }

    private fun initialState(request: RefreshRequest): RefreshCoordinatorState = when (request) {
        is RefreshRequest.ActiveQuery -> RefreshCoordinatorState(
            isLoading = true,
            isRefreshing = true,
            activeQuery = request.query,
        )

        is RefreshRequest.AcquireLocation -> {
            val location = locationStateMachine.state.value
            if (location.permissionState == LocationPermissionState.Denied || !location.isGpsEnabled) {
                RefreshCoordinatorState()
            } else {
                RefreshCoordinatorState(
                    isLoading = location.currentCoordinates == null,
                    isRefreshing = true,
                )
            }
        }
    }
}

private data class ActiveRefreshWork(val id: Long, val job: Job)

private sealed interface RefreshExecutionResult {
    data object Succeeded : RefreshExecutionResult

    data class Failed(val reason: StationRefreshFailureReason?) : RefreshExecutionResult
}
