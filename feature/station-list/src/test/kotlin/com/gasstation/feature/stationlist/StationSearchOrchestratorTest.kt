package com.gasstation.feature.stationlist

import app.cash.turbine.test
import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MoneyWon
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StationSearchOrchestratorTest {

    @Test
    fun `null query emits empty stale result`() = runTest {
        val orchestrator = stationSearchOrchestrator(ScriptedObservationRepository())

        orchestrator.observe(MutableStateFlow(null)).test {
            val result = awaitItem()

            assertEquals(canonicalEmptyResult(), result)
            assertFalse(orchestrator.observationFailed.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new query observes repository result`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val result = cachedResult()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        repository.latestSubscription(query).emit(result)
        runCurrent()

        assertEquals(listOf(query), repository.subscribedQueries)
        assertSame(result, orchestrator.searchResult.value)
        job.cancel()
    }

    @Test
    fun `failure keeps outer collector alive and preserves same query cached snapshots`() = runTest {
        val cachedSnapshots = listOf(
            cachedResult(),
            cachedResult().copy(stations = emptyList()),
        )

        cachedSnapshots.forEach { cachedSnapshot ->
            val repository = ScriptedObservationRepository()
            val orchestrator = stationSearchOrchestrator(repository)
            val query = stationQuery()
            val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
            runCurrent()

            val subscription = repository.latestSubscription(query)
            subscription.emit(cachedSnapshot)
            runCurrent()
            subscription.fail(IllegalStateException("observation failed"))
            runCurrent()

            assertTrue(orchestrator.observationFailed.value)
            assertSame(cachedSnapshot, orchestrator.searchResult.value)
            assertEquals(CachedSnapshotState.Present, orchestrator.activeQueryState.value.cacheState)
            assertTrue(job.isActive)
            job.cancel()
            runCurrent()
        }
    }

    @Test
    fun `same query retry coalesces rapid calls and recovers without clearing the result`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val cached = cachedResult()
        val recovered = cachedResult(stationId = "station-recovered").copy(freshness = StationFreshness.Fresh)
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        repository.latestSubscription(query).apply {
            emit(cached)
            runCurrent()
            fail(IllegalStateException("observation failed"))
        }
        runCurrent()

        orchestrator.retryObservation()
        orchestrator.retryObservation()
        assertSame(cached, orchestrator.searchResult.value)
        assertEquals(CachedSnapshotState.Present, orchestrator.activeQueryState.value.cacheState)
        runCurrent()

        assertEquals(2, repository.subscriptionCount(query))
        assertFalse(orchestrator.observationFailed.value)
        repository.latestSubscription(query).emit(recovered)
        runCurrent()
        assertSame(recovered, orchestrator.searchResult.value)
        job.cancel()
    }

    @Test
    fun `retry while healthy is a no-op and freshness emissions keep the subscription`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val fresh = cachedResult().copy(freshness = StationFreshness.Fresh)
        val stale = fresh.copy(freshness = StationFreshness.Stale)
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        val subscription = repository.latestSubscription(query)
        subscription.emit(fresh)
        runCurrent()
        orchestrator.retryObservation()
        subscription.emit(stale)
        runCurrent()

        assertEquals(1, repository.subscriptionCount(query))
        assertSame(stale, orchestrator.searchResult.value)
        assertFalse(orchestrator.observationFailed.value)
        job.cancel()
    }

    @Test
    fun `query change clears old unkeyed result before the new query fails`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val queryA = stationQuery()
        val queryB = queryA.copy(sortOrder = SortOrder.PRICE)
        val queryFlow = MutableStateFlow<StationQuery?>(queryA)
        val oldResult = cachedResult(stationId = "station-a")
        val job = launch { orchestrator.observe(queryFlow).collect {} }
        runCurrent()

        val oldSubscription = repository.latestSubscription(queryA)
        oldSubscription.emit(oldResult)
        runCurrent()
        oldSubscription.allowLateActionsAfterCancellation()
        queryFlow.value = queryB
        runCurrent()

        assertEquals(queryB, orchestrator.activeQueryState.value.query)
        assertEquals(CachedSnapshotState.Unknown, orchestrator.activeQueryState.value.cacheState)
        assertEquals(canonicalEmptyResult(), orchestrator.searchResult.value)
        assertTrue(oldSubscription.wasCancelled)
        assertEquals(1, repository.cancellationCount(queryA))

        oldSubscription.emit(cachedResult(stationId = "late-station-a"))
        oldSubscription.fail(IllegalStateException("late old-query failure"))
        runCurrent()
        repository.latestSubscription(queryB).fail(IllegalStateException("new query failed"))
        runCurrent()

        assertEquals(queryB, orchestrator.activeQueryState.value.query)
        assertEquals(CachedSnapshotState.Unknown, orchestrator.activeQueryState.value.cacheState)
        assertEquals(canonicalEmptyResult(), orchestrator.searchResult.value)
        assertTrue(orchestrator.observationFailed.value)
        job.cancel()
    }

    @Test
    fun `query change recovers from failure without explicit retry`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val queryA = stationQuery()
        val queryB = queryA.copy(brandFilter = BrandFilter.GSC)
        val queryFlow = MutableStateFlow<StationQuery?>(queryA)
        val recovered = cachedResult(stationId = "station-b")
        val job = launch { orchestrator.observe(queryFlow).collect {} }
        runCurrent()

        repository.latestSubscription(queryA).fail(IllegalStateException("query A failed"))
        runCurrent()
        assertTrue(orchestrator.observationFailed.value)

        queryFlow.value = queryB
        runCurrent()
        assertFalse(orchestrator.observationFailed.value)
        assertEquals(1, repository.subscriptionCount(queryB))

        repository.latestSubscription(queryB).emit(recovered)
        runCurrent()
        assertSame(recovered, orchestrator.searchResult.value)
        job.cancel()
    }

    @Test
    fun `failure before first result keeps cache unknown and blocking failure pending`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        orchestrator.onRefreshFailure(query, StationRefreshFailureReason.Unknown)
        repository.latestSubscription(query).fail(IllegalStateException("failed before first result"))
        runCurrent()

        assertTrue(orchestrator.observationFailed.value)
        assertEquals(CachedSnapshotState.Unknown, orchestrator.activeQueryState.value.cacheState)
        assertNull(orchestrator.blockingFailure.value)
        assertEquals(canonicalEmptyResult(), orchestrator.searchResult.value)
        job.cancel()
    }

    @Test
    fun `normal non null completion is recoverable by a same query retry`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val recovered = cachedResult(stationId = "station-after-completion")
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        repository.latestSubscription(query).complete()
        runCurrent()
        assertTrue(orchestrator.observationFailed.value)
        assertTrue(job.isActive)

        orchestrator.retryObservation()
        runCurrent()
        assertEquals(2, repository.subscriptionCount(query))
        repository.latestSubscription(query).emit(recovered)
        runCurrent()

        assertFalse(orchestrator.observationFailed.value)
        assertSame(recovered, orchestrator.searchResult.value)
        job.cancel()
    }

    @Test
    fun `cancellation is not observation failure and cancelled sessions cannot commit`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val queryA = stationQuery()
        val queryB = queryA.copy(radius = SearchRadius.KM_5)
        val queryFlow = MutableStateFlow<StationQuery?>(queryA)
        val resultB = cachedResult(stationId = "station-b")
        val job = launch { orchestrator.observe(queryFlow).collect {} }
        runCurrent()

        val subscriptionA = repository.latestSubscription(queryA)
        queryFlow.value = queryB
        runCurrent()
        val subscriptionB = repository.latestSubscription(queryB)
        subscriptionB.emit(resultB)
        runCurrent()

        assertTrue(subscriptionA.wasCancelled)
        assertEquals(1, repository.cancellationCount(queryA))
        assertFalse(orchestrator.observationFailed.value)

        subscriptionA.emit(cachedResult(stationId = "late-a"))
        subscriptionA.fail(IllegalStateException("late A failure"))
        runCurrent()
        assertSame(resultB, orchestrator.searchResult.value)
        assertFalse(orchestrator.observationFailed.value)

        job.cancel()
        runCurrent()
        assertTrue(subscriptionB.wasCancelled)
        assertEquals(1, repository.cancellationCount(queryB))
        assertFalse(orchestrator.observationFailed.value)

        val cancellationRepository = ScriptedObservationRepository()
        val cancellationOrchestrator = stationSearchOrchestrator(cancellationRepository)
        val cancellationJob = launch {
            cancellationOrchestrator.observe(MutableStateFlow<StationQuery?>(queryA)).collect {}
        }
        runCurrent()
        cancellationRepository.latestSubscription(queryA).fail(CancellationException("repository cancelled"))
        runCurrent()

        assertTrue(cancellationRepository.latestSubscription(queryA).wasCancelled)
        assertTrue(cancellationJob.isActive)
        assertFalse(cancellationOrchestrator.observationFailed.value)
        cancellationJob.cancel()
    }

    @Test
    fun `query change clears blocking failure`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val nextQuery = query.copy(radius = SearchRadius.KM_5)
        val queryFlow = MutableStateFlow<StationQuery?>(query)
        val job = launch { orchestrator.observe(queryFlow).collect {} }
        runCurrent()

        repository.latestSubscription(query).emit(noCacheResult())
        runCurrent()
        orchestrator.onRefreshFailure(query, StationRefreshFailureReason.Unknown)
        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)

        queryFlow.value = nextQuery
        runCurrent()

        assertNull(orchestrator.blockingFailure.value)
        job.cancel()
    }

    @Test
    fun `criteria change with same coordinates requires refresh`() {
        val orchestrator = stationSearchOrchestrator(ScriptedObservationRepository())
        val query = stationQuery()
        val nextQuery = query.copy(fuelType = FuelType.DIESEL)

        assertTrue(orchestrator.shouldRefreshForCriteriaChange(query, nextQuery))
    }

    @Test
    fun `criteria change with different coordinates does not count as criteria refresh`() {
        val orchestrator = stationSearchOrchestrator(ScriptedObservationRepository())
        val query = stationQuery()
        val nextQuery = query.copy(
            coordinates = Coordinates(37.500000, 127.030000),
            fuelType = FuelType.DIESEL,
        )

        assertFalse(orchestrator.shouldRefreshForCriteriaChange(query, nextQuery))
    }

    @Test
    fun `refresh success clears blocking failure for active query`() = runTest {
        val repository = ScriptedObservationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        repository.latestSubscription(query).emit(noCacheResult())
        runCurrent()
        orchestrator.onRefreshFailure(query, StationRefreshFailureReason.Unknown)
        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)

        assertEquals(RefreshOutcome.Success, orchestrator.refresh(query))

        assertNull(orchestrator.blockingFailure.value)
        assertEquals(listOf(query), repository.refreshedQueries)
        job.cancel()
    }

    @Test
    fun `refresh failure with cached snapshot does not expose blocking failure`() = runTest {
        val repository = ScriptedObservationRepository(
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        repository.latestSubscription(query).emit(cachedResult())
        runCurrent()
        val outcome = orchestrator.refresh(query)
        orchestrator.onRefreshFailure(query, (outcome as RefreshOutcome.Failed).reason)

        assertEquals(RefreshOutcome.Failed(StationRefreshFailureReason.Unknown), outcome)
        assertNull(orchestrator.blockingFailure.value)
        job.cancel()
    }

    @Test
    fun `refresh failure without cached snapshot exposes blocking failure`() = runTest {
        val repository = ScriptedObservationRepository(
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        repository.latestSubscription(query).emit(noCacheResult())
        runCurrent()
        val outcome = orchestrator.refresh(query)
        orchestrator.onRefreshFailure(query, (outcome as RefreshOutcome.Failed).reason)

        assertEquals(RefreshOutcome.Failed(StationRefreshFailureReason.Unknown), outcome)
        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)
        job.cancel()
    }

    @Test
    fun `refresh failure before cache state is known waits for observed result`() = runTest {
        val repository = ScriptedObservationRepository(
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        runCurrent()

        val outcome = orchestrator.refresh(query)
        orchestrator.onRefreshFailure(query, (outcome as RefreshOutcome.Failed).reason)
        assertNull(orchestrator.blockingFailure.value)

        repository.latestSubscription(query).emit(noCacheResult())
        runCurrent()

        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)
        job.cancel()
    }
}

private fun stationSearchOrchestrator(repository: StationRepository): StationSearchOrchestrator = StationSearchOrchestrator(
    observeNearbyStations = ObserveNearbyStationsUseCase(repository),
    refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
)

private class ScriptedObservationRepository(var refreshFailure: Throwable? = null) : StationRepository {
    private val subscriptions = mutableMapOf<StationQuery, MutableList<ObservationSubscription>>()

    val subscribedQueries = mutableListOf<StationQuery>()
    val refreshedQueries = mutableListOf<StationQuery>()

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = object : Flow<StationSearchResult> {
        @OptIn(InternalCoroutinesApi::class)
        override suspend fun collect(collector: FlowCollector<StationSearchResult>) {
            val subscription = ObservationSubscription(query)
            subscribedQueries += query
            subscriptions.getOrPut(query, ::mutableListOf) += subscription

            try {
                while (true) {
                    when (val action = subscription.actions.receive()) {
                        is ObservationAction.Emit -> collector.emit(action.result)
                        is ObservationAction.Fail -> throw action.throwable
                        ObservationAction.Complete -> return
                    }
                }
            } catch (cancellationException: CancellationException) {
                subscription.wasCancelled = true
                if (!subscription.acceptLateActionsAfterCancellation) throw cancellationException

                while (true) {
                    when (val action = withContext(NonCancellable) { subscription.actions.receive() }) {
                        is ObservationAction.Emit -> withContext(NonCancellable) { collector.emit(action.result) }
                        is ObservationAction.Fail -> throw action.throwable
                        ObservationAction.Complete -> throw cancellationException
                    }
                }
            }
        }
    }

    fun latestSubscription(query: StationQuery): ObservationSubscription = checkNotNull(subscriptions[query]?.lastOrNull()) {
        "No active subscription for $query"
    }

    fun subscriptionCount(query: StationQuery): Int = subscriptions[query]?.size ?: 0

    fun cancellationCount(query: StationQuery): Int = subscriptions[query]?.count(ObservationSubscription::wasCancelled) ?: 0

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = flow { emit(emptyList()) }

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshFailure?.let { throw it }
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) =
        com.gasstation.domain.station.model.WatchMutationResult.Committed

    override suspend fun removeWatchedStation(stationId: String) = com.gasstation.domain.station.model.WatchMutationResult.Committed
}

private class ObservationSubscription(val query: StationQuery) {
    internal val actions = Channel<ObservationAction>(Channel.UNLIMITED)
    var wasCancelled: Boolean = false
        internal set
    internal var acceptLateActionsAfterCancellation: Boolean = false

    fun allowLateActionsAfterCancellation() {
        acceptLateActionsAfterCancellation = true
    }

    fun emit(result: StationSearchResult) {
        check(actions.trySend(ObservationAction.Emit(result)).isSuccess)
    }

    fun fail(throwable: Throwable) {
        check(actions.trySend(ObservationAction.Fail(throwable)).isSuccess)
    }

    fun complete() {
        check(actions.trySend(ObservationAction.Complete).isSuccess)
    }
}

private sealed interface ObservationAction {
    data class Emit(val result: StationSearchResult) : ObservationAction
    data class Fail(val throwable: Throwable) : ObservationAction
    data object Complete : ObservationAction
}

private fun stationQuery(coordinates: Coordinates = Coordinates(37.498095, 127.027610)): StationQuery = StationQuery(
    coordinates = coordinates,
    radius = SearchRadius.KM_3,
    fuelType = FuelType.GASOLINE,
    brandFilter = BrandFilter.ALL,
    sortOrder = SortOrder.DISTANCE,
)

private fun cachedResult(stationId: String = "station-1"): StationSearchResult = StationSearchResult(
    stations = listOf(
        StationListEntry(
            station = Station(
                id = stationId,
                name = "강남주유소",
                brand = Brand.GSC,
                price = MoneyWon(1_689),
                distance = DistanceMeters(800),
                coordinates = Coordinates(37.499095, 127.027610),
            ),
            priceDelta = StationPriceDelta.Unavailable,
            isWatched = false,
            lastSeenAt = Instant.parse("2026-04-18T01:00:00Z"),
        ),
    ),
    freshness = StationFreshness.Stale,
    fetchedAt = Instant.parse("2026-04-18T01:00:00Z"),
    hasCachedSnapshot = true,
)

private fun noCacheResult(): StationSearchResult = canonicalEmptyResult()

private fun canonicalEmptyResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)
