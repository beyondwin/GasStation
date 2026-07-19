package com.gasstation

import android.Manifest
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import javax.inject.Inject
import kotlin.math.roundToInt

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
    fun demoFilterMenu_dismissesWithSystemBack_keepsNearbyActive() {
        reseedDemoDatabase()
        openRadiusFilterMenu()

        Espresso.pressBack()
        waitForFilterMenuDismissal()
        assertNearbyRemainsActive()
    }

    @Test
    fun demoFilterMenu_dismissesWithInAppOutsideTap_keepsNearbyActive() {
        reseedDemoDatabase()
        openRadiusFilterMenu()

        tapInertTitleAreaOutsideFilterMenu()
        waitForFilterMenuDismissal()
        assertNearbyRemainsActive()
    }

    private fun openRadiusFilterMenu() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("station-list-filter-radius", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("station-list-filter-radius", useUnmergedTree = true).performClick()
        waitForFilterMenu()
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

    private fun tapInertTitleAreaOutsideFilterMenu() {
        val decorView = rule.activity.window.decorView
        val decorLocation = IntArray(2).also(decorView::getLocationOnScreen)
        val safeContentBounds = appVisibleSafeContentBoundsInScreen(decorView, decorLocation)
        val popupBoundsInScreen = rule.onNodeWithTag(
            "station-list-filter-menu",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInScreen()
        val titleNode = rule.onNodeWithTag(
            "station-list-title",
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val titleBoundsInScreen = titleNode.boundsInScreen()
        val x = titleBoundsInScreen.center.x.roundToInt()
        val y = titleBoundsInScreen.center.y.roundToInt()

        assertFalse("Title tap target must remain inert", titleNode.config.contains(SemanticsActions.OnClick))
        assertTrue("Title tap must be inside app-visible safe content", safeContentBounds.contains(x, y))
        assertFalse(
            "Title tap must be outside popup bounds",
            popupBoundsInScreen.contains(Offset(x.toFloat(), y.toFloat())),
        )

        val eventTime = SystemClock.uptimeMillis()
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0).also { event ->
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        MotionEvent.obtain(eventTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0).also { event ->
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
    }

    private fun appVisibleSafeContentBoundsInScreen(decorView: View, decorLocation: IntArray): Rect {
        val visibleFrame = Rect().also(decorView::getWindowVisibleDisplayFrame)
        val systemBars = ViewCompat.getRootWindowInsets(decorView)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?: error("System bar insets must be available before injecting an outside tap")
        val edgeMargin = (24 * decorView.resources.displayMetrics.density).roundToInt()
        return Rect(
            maxOf(visibleFrame.left, decorLocation[0] + systemBars.left) + edgeMargin,
            maxOf(visibleFrame.top, decorLocation[1] + systemBars.top) + edgeMargin,
            minOf(visibleFrame.right, decorLocation[0] + decorView.width - systemBars.right) - edgeMargin,
            minOf(visibleFrame.bottom, decorLocation[1] + decorView.height - systemBars.bottom) - edgeMargin,
        ).also { bounds ->
            assertTrue("Safe app content bounds must be non-empty", bounds.width() > 0 && bounds.height() > 0)
        }
    }

    private fun assertNearbyRemainsActive() {
        assertFalse("Outside tap must not finish MainActivity", rule.activity.isFinishing)
        assertFalse("Outside tap must not destroy MainActivity", rule.activity.isDestroyed)
        rule.onNodeWithTag("bottom-nav-nearby", useUnmergedTree = true).assertIsSelected()
        rule.onNodeWithTag("station-list-filter-radius", useUnmergedTree = true).fetchSemanticsNode()
    }

    private fun SemanticsNode.boundsInScreen(): ComposeRect = boundsInWindow.toScreenBounds(
        positionInWindow = positionInWindow,
        positionOnScreen = positionOnScreen,
    )

    private fun ComposeRect.toScreenBounds(positionInWindow: Offset, positionOnScreen: Offset): ComposeRect {
        val windowToScreenX = positionOnScreen.x - positionInWindow.x
        val windowToScreenY = positionOnScreen.y - positionInWindow.y
        return ComposeRect(
            left + windowToScreenX,
            top + windowToScreenY,
            right + windowToScreenX,
            bottom + windowToScreenY,
        )
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
