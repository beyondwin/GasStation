package com.gasstation.feature.watchlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.model.Brand
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.station.StationEventLogger
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchMutationResult
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.model.WatchlistQuery
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import com.gasstation.domain.station.usecase.RemoveWatchedStationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko-rKR-w360dp-h800dp-xhdpi")
class WatchlistRouteFeedbackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: WatchlistViewModel
    private var attached by mutableStateOf(true)

    @After
    fun clearViewModel() {
        composeRule.runOnUiThread { viewModelStore.clear() }
    }

    @Test
    fun `leaving route retains failed removal feedback and reattachment displays it until dismissed`() {
        renderRoute()
        val failureId = failRemovalAndAssertFeedback()

        composeRule.runOnUiThread { attached = false }
        composeRule.waitForIdle()
        assertEquals(failureId, viewModel.uiState.value.pendingRemovalFailureId)

        composeRule.runOnUiThread { attached = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(FAILURE_MESSAGE).assertIsDisplayed()
        assertEquals(failureId, viewModel.uiState.value.pendingRemovalFailureId)

        dismissFeedbackAndAssertAcknowledged()
    }

    @Test
    fun `STOP retains failed removal feedback and START displays it until dismissed`() {
        renderRoute()
        val failureId = failRemovalAndAssertFeedback()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        assertEquals(failureId, viewModel.uiState.value.pendingRemovalFailureId)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(FAILURE_MESSAGE).assertIsDisplayed()
        assertEquals(failureId, viewModel.uiState.value.pendingRemovalFailureId)

        dismissFeedbackAndAssertAcknowledged()
    }

    private fun renderRoute() {
        composeRule.runOnUiThread {
            val repository = FailingRemovalRepository()
            val factory = viewModelFactory {
                initializer {
                    WatchlistViewModel(
                        observeWatchlist = ObserveWatchlistUseCase(repository),
                        observeUserPreferences = ObserveUserPreferencesUseCase { MutableStateFlow(UserPreferences.default()) },
                        removeWatchedStation = RemoveWatchedStationUseCase(repository),
                        savedStateHandle = SavedStateHandle(mapOf("latitude" to 37.498095, "longitude" to 127.027610)),
                        stationEventLogger = object : StationEventLogger {
                            override fun log(event: StationEvent) = Unit
                        },
                    )
                }
            }
            viewModel = ViewModelProvider.create(viewModelStore, factory)[WatchlistViewModel::class]
        }
        composeRule.setContent {
            GasStationTheme {
                if (attached) WatchlistRoute(onNavigateNearby = {}, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun failRemovalAndAssertFeedback(): Long {
        composeRule.onNodeWithContentDescription("관심 주유소에서 제거").performClick()
        composeRule.onNodeWithText(FAILURE_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("테스트 주유소").assertIsDisplayed()
        return checkNotNull(viewModel.uiState.value.pendingRemovalFailureId)
    }

    private fun dismissFeedbackAndAssertAcknowledged() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { dismiss -> dismiss() }
        composeRule.waitUntil { viewModel.uiState.value.pendingRemovalFailureId == null }
        composeRule.onNodeWithText("테스트 주유소").assertIsDisplayed()
        assertEquals(1, viewModel.uiState.value.stations.size)
    }
}

private const val FAILURE_MESSAGE = "관심 주유소에서 제거하지 못했습니다. 다시 눌러주세요."

private class FailingRemovalRepository : StationRepository {
    private val summaries = MutableStateFlow(
        listOf(
            WatchedStationSummary(
                id = "station-1",
                name = "테스트 주유소",
                brand = Brand.GSC,
                price = MoneyWon(1_680),
                distance = DistanceMeters(300),
                coordinates = Coordinates(37.498095, 127.027610),
                priceDelta = StationPriceDelta.Unchanged,
                lastSeenAt = null,
            ),
        ),
    )

    override fun observeWatchlist(query: WatchlistQuery): Flow<List<WatchedStationSummary>> = summaries

    override suspend fun removeWatchedStation(stationId: String): WatchMutationResult = error("disk write failed")

    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> = error("Nearby is not used by watchlist")

    override suspend fun refreshNearbyStations(query: StationQuery): Unit = error("Watchlist does not refresh Nearby")

    override suspend fun updateWatchState(station: Station, watched: Boolean): WatchMutationResult = error("Watchlist only removes")
}
