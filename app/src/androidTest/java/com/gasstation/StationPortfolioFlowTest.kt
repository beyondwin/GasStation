package com.gasstation

import android.Manifest
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.demo.seed.DemoSeedAssetLoader
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.feature.watchlist.WATCHLIST_CARD_TEST_TAG
import com.gasstation.startup.DemoSeedStartupHook
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StationPortfolioFlowTest {
    @Inject
    lateinit var database: GasStationDatabase

    @Inject
    lateinit var assetLoader: DemoSeedAssetLoader

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val locationPermissionRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val packageName = instrumentation.targetContext.packageName
                instrumentation.uiAutomation.grantRuntimePermission(
                    packageName,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                instrumentation.uiAutomation.grantRuntimePermission(
                    packageName,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun demoFlow_can_watch_station_and_open_watchlist() {
        reseedDemoDatabase()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("station-list-watch-toggle", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithTag("station-list-watch-toggle", useUnmergedTree = true)
            .onFirst()
            .performClick()

        rule.onNodeWithTag("bottom-nav-watchlist", useUnmergedTree = true).performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(
                WATCHLIST_CARD_TEST_TAG,
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun demoFilterMenu_dismissesWithSystemBackAndOutsideTap() {
        reseedDemoDatabase()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("station-list-filter-radius", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("station-list-filter-radius", useUnmergedTree = true).performClick()
        waitForFilterMenu()

        Espresso.pressBack()
        waitForFilterMenuDismissal()

        rule.onNodeWithTag("station-list-filter-radius", useUnmergedTree = true).performClick()
        waitForFilterMenu()

        tapOutsideFilterMenu()
        waitForFilterMenuDismissal()
    }

    private fun waitForFilterMenu() {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("station-list-filter-menu", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForFilterMenuDismissal() {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("station-list-filter-menu", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapOutsideFilterMenu() {
        val decorView = rule.activity.window.decorView
        val eventTime = SystemClock.uptimeMillis()
        val x = decorView.width.toFloat() - 1f
        val y = decorView.height.toFloat() - 1f
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0).also { event ->
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        MotionEvent.obtain(eventTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).also { event ->
            instrumentation.sendPointerSync(event)
            event.recycle()
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
