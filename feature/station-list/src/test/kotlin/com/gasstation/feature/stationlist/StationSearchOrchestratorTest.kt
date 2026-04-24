package com.gasstation.feature.stationlist

import app.cash.turbine.test
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.station.StationRefreshException
import com.gasstation.domain.station.StationRefreshFailureReason
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.usecase.ObserveNearbyStationsUseCase
import com.gasstation.domain.station.usecase.RefreshNearbyStationsUseCase
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationSearchOrchestratorTest {

    @Test
    fun `null query emits empty stale result`() = runTest {
        val orchestrator = stationSearchOrchestrator(FakeOrchestratorStationRepository())

        orchestrator.observe(MutableStateFlow(null)).test {
            val result = awaitItem()

            assertTrue(result.stations.isEmpty())
            assertEquals(StationFreshness.Stale, result.freshness)
            assertNull(result.fetchedAt)
            assertFalse(result.hasCachedSnapshot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new query observes repository result`() = runTest {
        val repository = FakeOrchestratorStationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val result = cachedResult()

        orchestrator.observe(MutableStateFlow(query)).test {
            repository.emit(query, result)

            assertEquals(result, awaitItem())
            assertEquals(listOf(query), repository.observedQueries)
            assertEquals(result, orchestrator.searchResult.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query change clears blocking failure`() = runTest {
        val repository = FakeOrchestratorStationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val nextQuery = query.copy(radius = SearchRadius.KM_5)
        val queryFlow = MutableStateFlow<StationQuery?>(query)
        val job = launch { orchestrator.observe(queryFlow).collect {} }
        advanceUntilIdle()

        repository.emit(query, noCacheResult())
        advanceUntilIdle()
        orchestrator.onRefreshFailure(query, StationRefreshFailureReason.Unknown)
        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)

        queryFlow.value = nextQuery
        advanceUntilIdle()

        assertNull(orchestrator.blockingFailure.value)
        job.cancel()
    }

    @Test
    fun `criteria change with same coordinates requires refresh`() {
        val orchestrator = stationSearchOrchestrator(FakeOrchestratorStationRepository())
        val query = stationQuery()
        val nextQuery = query.copy(fuelType = FuelType.DIESEL)

        assertTrue(orchestrator.shouldRefreshForCriteriaChange(query, nextQuery))
    }

    @Test
    fun `criteria change with different coordinates does not count as criteria refresh`() {
        val orchestrator = stationSearchOrchestrator(FakeOrchestratorStationRepository())
        val query = stationQuery()
        val nextQuery = query.copy(
            coordinates = Coordinates(37.500000, 127.030000),
            fuelType = FuelType.DIESEL,
        )

        assertFalse(orchestrator.shouldRefreshForCriteriaChange(query, nextQuery))
    }

    @Test
    fun `refresh success clears blocking failure for active query`() = runTest {
        val repository = FakeOrchestratorStationRepository()
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        advanceUntilIdle()

        repository.emit(query, noCacheResult())
        advanceUntilIdle()
        orchestrator.onRefreshFailure(query, StationRefreshFailureReason.Unknown)
        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)

        assertEquals(RefreshOutcome.Success, orchestrator.refresh(query))

        assertNull(orchestrator.blockingFailure.value)
        assertEquals(listOf(query), repository.refreshedQueries)
        job.cancel()
    }

    @Test
    fun `refresh failure with cached snapshot does not expose blocking failure`() = runTest {
        val repository = FakeOrchestratorStationRepository(
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        advanceUntilIdle()

        repository.emit(query, cachedResult())
        advanceUntilIdle()
        val outcome = orchestrator.refresh(query)
        orchestrator.onRefreshFailure(query, (outcome as RefreshOutcome.Failed).reason)

        assertEquals(RefreshOutcome.Failed(StationRefreshFailureReason.Unknown), outcome)
        assertNull(orchestrator.blockingFailure.value)
        job.cancel()
    }

    @Test
    fun `refresh failure without cached snapshot exposes blocking failure`() = runTest {
        val repository = FakeOrchestratorStationRepository(
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        advanceUntilIdle()

        repository.emit(query, noCacheResult())
        advanceUntilIdle()
        val outcome = orchestrator.refresh(query)
        orchestrator.onRefreshFailure(query, (outcome as RefreshOutcome.Failed).reason)

        assertEquals(RefreshOutcome.Failed(StationRefreshFailureReason.Unknown), outcome)
        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)
        job.cancel()
    }

    @Test
    fun `refresh failure before cache state is known waits for observed result`() = runTest {
        val repository = FakeOrchestratorStationRepository(
            refreshFailure = StationRefreshException(StationRefreshFailureReason.Unknown),
        )
        val orchestrator = stationSearchOrchestrator(repository)
        val query = stationQuery()
        val job = launch { orchestrator.observe(MutableStateFlow<StationQuery?>(query)).collect {} }
        advanceUntilIdle()

        val outcome = orchestrator.refresh(query)
        orchestrator.onRefreshFailure(query, (outcome as RefreshOutcome.Failed).reason)
        assertNull(orchestrator.blockingFailure.value)

        repository.emit(query, noCacheResult())
        advanceUntilIdle()

        assertEquals(StationListFailureReason.RefreshFailed, orchestrator.blockingFailure.value)
        job.cancel()
    }
}

private fun stationSearchOrchestrator(
    repository: StationRepository,
): StationSearchOrchestrator = StationSearchOrchestrator(
    observeNearbyStations = ObserveNearbyStationsUseCase(repository),
    refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
)

private class FakeOrchestratorStationRepository(
    var refreshFailure: Throwable? = null,
) : StationRepository {
    private val resultFlows = mutableMapOf<StationQuery, MutableSharedFlow<StationSearchResult>>()

    val observedQueries = mutableListOf<StationQuery>()
    val refreshedQueries = mutableListOf<StationQuery>()

    fun emit(query: StationQuery, result: StationSearchResult) {
        resultFlow(query).tryEmit(result)
    }

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        observedQueries += query
        return resultFlow(query)
    }

    override fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>> =
        MutableStateFlow(emptyList())

    override suspend fun refreshNearbyStations(query: StationQuery) {
        refreshedQueries += query
        refreshFailure?.let { throw it }
    }

    override suspend fun updateWatchState(station: Station, watched: Boolean) = Unit

    private fun resultFlow(query: StationQuery): MutableSharedFlow<StationSearchResult> =
        resultFlows.getOrPut(query) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 1)
        }
}

private fun stationQuery(
    coordinates: Coordinates = Coordinates(37.498095, 127.027610),
): StationQuery = StationQuery(
    coordinates = coordinates,
    radius = SearchRadius.KM_3,
    fuelType = FuelType.GASOLINE,
    brandFilter = BrandFilter.ALL,
    sortOrder = SortOrder.DISTANCE,
)

private fun cachedResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = Instant.parse("2026-04-18T01:00:00Z"),
    hasCachedSnapshot = true,
)

private fun noCacheResult(): StationSearchResult = StationSearchResult(
    stations = emptyList(),
    freshness = StationFreshness.Stale,
    fetchedAt = null,
    hasCachedSnapshot = false,
)
