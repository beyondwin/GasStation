package com.gasstation.feature.stationlist

import com.gasstation.domain.location.LocationPermissionState
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.StationFreshness
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationListObservationRecoveryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `visible refresh recovers failed same query observation while retaining cached rows`() = runTest(mainDispatcherRule.dispatcher) {
        val cached = cachedResult()
        val repository = RecoverableObservationRepository(cached)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        val originalRows = viewModel.uiState.value.stations
        val initialRefreshCount = repository.delegate.refreshedQueries.size

        repository.failObservation.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.observationFailed)
        assertEquals(originalRows, viewModel.uiState.value.stations)
        assertEquals(StationListBodyState.Results, viewModel.uiState.value.toBodyState())
        assertEquals(
            R.string.station_list_observation_failed_title,
            StationListBannerModel.from(viewModel.uiState.value).single().titleResId,
        )

        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()

        assertEquals(2, repository.subscriptionCount)
        assertEquals(initialRefreshCount + 1, repository.delegate.refreshedQueries.size)
        assertFalse(viewModel.uiState.value.observationFailed)
        assertEquals(originalRows, viewModel.uiState.value.stations)
        assertTrue(StationListBannerModel.from(viewModel.uiState.value).isEmpty())
    }

    @Test
    fun `initial observation failure exposes retry instead of indefinite loading`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecoverableObservationRepository(initial = null)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        repository.failObservation.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            StationListBodyState.Failure(StationListFailureReason.ObservationFailed),
            viewModel.uiState.value.toBodyState(),
        )
        val refreshCount = repository.delegate.refreshedQueries.size

        viewModel.onAction(StationListAction.RetryClicked)
        advanceUntilIdle()

        assertEquals(2, repository.subscriptionCount)
        assertEquals(refreshCount, repository.delegate.refreshedQueries.size)
        assertFalse(viewModel.uiState.value.observationFailed)
        assertEquals(StationListBodyState.Results, viewModel.uiState.value.toBodyState())
    }

    @Test
    fun `completed observation retains successful empty snapshot and exposes refresh guidance`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecoverableObservationRepository(cachedResult().copy(stations = emptyList()), completeNormally = true)
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default()),
            locationRepository = FakeLocationRepository(),
        )
        viewModel.onAction(StationListAction.PermissionChanged(LocationPermissionState.PreciseGranted))
        viewModel.onAction(StationListAction.GpsAvailabilityChanged(true))
        viewModel.onAction(StationListAction.RefreshRequested)
        advanceUntilIdle()
        repository.failObservation.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.observationFailed)
        assertTrue(viewModel.uiState.value.hasCachedSnapshot)
        assertTrue(viewModel.uiState.value.stations.isEmpty())
        assertEquals(StationListBodyState.Results, viewModel.uiState.value.toBodyState())
        assertEquals(
            R.string.station_list_observation_failed_title,
            StationListBannerModel.from(viewModel.uiState.value).single().titleResId,
        )
    }
}

private class RecoverableObservationRepository(
    private val initial: StationSearchResult?,
    private val completeNormally: Boolean = false,
    val delegate: FakeStationRepository = FakeStationRepository(cachedResult()),
) : StationRepository by delegate {
    val failObservation = CompletableDeferred<Unit>()
    var subscriptionCount = 0
        private set

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = flow {
        subscriptionCount += 1
        if (subscriptionCount == 1) {
            initial?.let { emit(it) }
            failObservation.await()
            if (!completeNormally) error("database observer failed")
        } else {
            emit(initial ?: cachedResult())
            awaitCancellation()
        }
    }
}

private fun cachedResult() = StationSearchResult(
    stations = listOf(stationEntry()),
    freshness = StationFreshness.Fresh,
    fetchedAt = null,
    hasCachedSnapshot = true,
)
