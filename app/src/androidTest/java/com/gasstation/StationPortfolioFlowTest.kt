package com.gasstation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.demo.seed.DemoSeedAssetLoader
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.feature.watchlist.WATCHLIST_CARD_CONTENT_DESCRIPTION
import com.gasstation.startup.DemoSeedStartupHook
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StationPortfolioFlowTest {
    @Inject
    lateinit var database: GasStationDatabase

    @Inject
    lateinit var assetLoader: DemoSeedAssetLoader

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun demoFlow_can_watch_station_and_open_watchlist() {
        reseedDemoDatabase()
        rule.activityRule.scenario.recreate()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("관심 주유소 토글")
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithContentDescription("관심 주유소 토글")
            .onFirst()
            .performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("관심 비교")
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("관심 비교").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(
                WATCHLIST_CARD_CONTENT_DESCRIPTION,
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun reseedDemoDatabase() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val document = assetLoader.load(application)
        DemoSeedStartupHook(assetLoader, settingsRepository)
            .seedDatabase(database = database, document = document)
        runBlocking {
            settingsRepository.updateUserPreferences { com.gasstation.domain.settings.model.UserPreferences.default() }
        }
    }
}
