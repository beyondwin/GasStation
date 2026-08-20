package com.gasstation

import android.Manifest
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.demo.seed.DemoSeedAssetLoader
import com.gasstation.demo.seed.DemoSeedOrigin
import com.gasstation.di.ExternalMapModule
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.StationRepository
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationQuery
import com.gasstation.feature.watchlist.WATCHLIST_CARD_TEST_TAG
import com.gasstation.map.ExternalMapLaunchResult
import com.gasstation.map.ExternalMapLauncher
import com.gasstation.startup.DemoSeedStartupHook
import com.gasstation.test.DeviceFailureArtifactRule
import com.gasstation.test.DevicePrSmoke
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect
import com.gasstation.core.designsystem.R as DesignSystemR
import com.gasstation.feature.settings.R as SettingsR
import com.gasstation.feature.stationlist.R as StationListR
import com.gasstation.feature.watchlist.R as WatchlistR

@UninstallModules(ExternalMapModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StationPortfolioFlowTest {
    @Inject
    lateinit var database: GasStationDatabase

    @Inject
    lateinit var assetLoader: DemoSeedAssetLoader

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var stationRepository: StationRepository

    @BindValue
    @JvmField
    val externalMapLauncher: ExternalMapLauncher = RecordingExternalMapLauncher()

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 3)
    val failureArtifacts = DeviceFailureArtifactRule()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @DevicePrSmoke
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

    @Test
    fun demoSettingsAndNearby_sharePersistedPreferencesAcrossNavigationAndRecreation() {
        reseedDemoDatabase()
        waitForNearby()

        selectNearbyFilter("station-list-filter-radius", "station-list-filter-option-KM_5")
        selectNearbyFilter("station-list-filter-fuel", "station-list-filter-option-DIESEL")
        selectNearbyFilter("station-list-filter-brand", "station-list-filter-option-GSC")
        rule.onNodeWithText(
            targetString(StationListR.string.station_list_sort_distance),
            useUnmergedTree = true,
        ).performClick()

        awaitPreferences("Nearby writes") { preferences ->
            preferences.searchRadius == SearchRadius.KM_5 &&
                preferences.fuelType == FuelType.DIESEL &&
                preferences.brandFilter == BrandFilter.GSC &&
                preferences.sortOrder == SortOrder.PRICE
        }

        rule.onNodeWithTag("bottom-nav-settings", useUnmergedTree = true).performClick()
        rule.onNodeWithText(targetString(DesignSystemR.string.gas_station_radius_km5)).assertExists()
        rule.onNodeWithText(targetString(DesignSystemR.string.gas_station_fuel_diesel)).assertExists()
        rule.onNodeWithText("GS칼텍스").assertExists()
        rule.onNodeWithText(targetString(SettingsR.string.settings_sort_price_label)).assertExists()

        selectSetting("settings-row-search-radius", "settings-option-KM_4")
        selectSetting("settings-row-fuel-type", "settings-option-GASOLINE")
        selectSetting("settings-row-brand-filter", "settings-option-ALL")
        selectSetting("settings-row-sort-order", "settings-option-DISTANCE")

        awaitPreferences("Settings writes") { preferences ->
            preferences.searchRadius == SearchRadius.KM_4 &&
                preferences.fuelType == FuelType.GASOLINE &&
                preferences.brandFilter == BrandFilter.ALL &&
                preferences.sortOrder == SortOrder.DISTANCE
        }

        rule.onNodeWithTag("bottom-nav-nearby", useUnmergedTree = true).performClick()
        assertNearbyFilters(
            radius = targetString(DesignSystemR.string.gas_station_radius_km4),
            fuel = targetString(DesignSystemR.string.gas_station_fuel_gasoline),
            brand = "전체",
            sort = targetString(StationListR.string.station_list_sort_distance),
        )

        rule.activityRule.scenario.recreate()
        waitForNearby()
        awaitPreferences("Recreated activity") { preferences ->
            preferences.searchRadius == SearchRadius.KM_4 &&
                preferences.fuelType == FuelType.GASOLINE &&
                preferences.brandFilter == BrandFilter.ALL &&
                preferences.sortOrder == SortOrder.DISTANCE
        }
        assertNearbyFilters(
            radius = targetString(DesignSystemR.string.gas_station_radius_km4),
            fuel = targetString(DesignSystemR.string.gas_station_fuel_gasoline),
            brand = "전체",
            sort = targetString(StationListR.string.station_list_sort_distance),
        )
    }

    @Test
    fun demoWatchlist_keepsSavedRowAndUsesSelectedFuelContext() {
        reseedDemoDatabase()
        waitForNearby()
        val gasolineOnlyStation = seedGasolineOnlyWatchlistStation()

        rule.onNodeWithTag("bottom-nav-watchlist", useUnmergedTree = true).performClick()
        rule.onNodeWithText(
            targetString(
                WatchlistR.string.watchlist_fuel_context,
                targetString(DesignSystemR.string.gas_station_fuel_gasoline),
            ),
        ).assertExists()
        rule.onAllNodesWithTag(WATCHLIST_CARD_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onNodeWithText(gasolineOnlyStation.name).assertExists()
        rule.onNode(
            matcher = hasText("1,987") and hasAnyAncestor(hasTestTag(WATCHLIST_CARD_TEST_TAG)),
            useUnmergedTree = true,
        ).assertExists()

        rule.onNodeWithTag("bottom-nav-settings", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("settings-row-fuel-type", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("settings-option-DIESEL", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("bottom-nav-watchlist", useUnmergedTree = true).performClick()

        rule.onNodeWithText(
            targetString(
                WatchlistR.string.watchlist_fuel_context,
                targetString(DesignSystemR.string.gas_station_fuel_diesel),
            ),
        ).assertExists()
        rule.onAllNodesWithTag(WATCHLIST_CARD_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
        rule.onNodeWithText(gasolineOnlyStation.name).assertExists()
        rule.onNodeWithText(targetString(WatchlistR.string.watchlist_price_unavailable)).assertExists()
    }

    @Test
    fun demoMapSelection_isConsumedByNearbyHandoff() {
        reseedDemoDatabase()
        waitForNearby()

        rule.onNodeWithTag("bottom-nav-settings", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("settings-row-map-provider", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("settings-option-NAVER_MAP", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("bottom-nav-nearby", useUnmergedTree = true).performClick()
        rule.onAllNodesWithTag("station-list-row", useUnmergedTree = true)
            .onFirst()
            .performClick()

        assertEquals(
            listOf(MapProvider.NAVER_MAP),
            (externalMapLauncher as RecordingExternalMapLauncher).providers,
        )
    }

    private fun selectNearbyFilter(chipTag: String, optionTag: String) {
        rule.onNodeWithTag(chipTag, useUnmergedTree = true).performClick()
        rule.onNodeWithTag(optionTag, useUnmergedTree = true).performClick()
        val expectedEnumName = optionTag.substringAfterLast('-')
        awaitPreferences("$chipTag selection") { preferences ->
            when (chipTag) {
                "station-list-filter-radius" -> preferences.searchRadius.name == expectedEnumName
                "station-list-filter-fuel" -> preferences.fuelType.name == expectedEnumName
                "station-list-filter-brand" -> preferences.brandFilter.name == expectedEnumName
                else -> error("Unsupported Nearby filter tag: $chipTag")
            }
        }
    }

    private fun awaitPreferences(label: String, predicate: (UserPreferences) -> Boolean) {
        val observed = runBlocking {
            withTimeoutOrNull(10_000) {
                while (true) {
                    settingsRepository.observeUserPreferences().first()
                        .takeIf(predicate)
                        ?.let { return@withTimeoutOrNull it }
                    delay(50)
                }
            }
        }
        if (observed == null) {
            val current = runBlocking {
                withTimeout(5_000) {
                    settingsRepository.observeUserPreferences().first()
                }
            }
            fail("$label did not persist within 10 seconds; current=$current")
        }
    }

    private fun selectSetting(rowTag: String, optionTag: String) {
        rule.onNodeWithTag(rowTag, useUnmergedTree = true).performClick()
        rule.onNodeWithTag(optionTag, useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("settings-screen-list", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNearby() {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(
                "station-list-watch-toggle",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNearbyFilters(radius: String, fuel: String, brand: String, sort: String) {
        rule.onNodeWithTag("station-list-filter-radius").assertTextEquals(radius)
        rule.onNodeWithTag("station-list-filter-fuel").assertTextEquals(fuel)
        rule.onNodeWithTag("station-list-filter-brand").assertTextEquals(brand)
        rule.onNode(
            matcher = hasText(sort) and hasAnyAncestor(hasTestTag("station-list-filter-rail")),
            useUnmergedTree = true,
        ).assertTextEquals(sort)
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

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)

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
            settingsRepository.updateUserPreferences { UserPreferences.default() }
        }
    }

    private fun seedGasolineOnlyWatchlistStation(): Station = runBlocking {
        val query = StationQuery(
            coordinates = DemoSeedOrigin.coordinates,
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.DISTANCE,
        )
        stationRepository.refreshNearbyStations(query)
        val station = withTimeout(5_000) {
            stationRepository.observeNearbyStations(query)
                .first { result ->
                    result.stations.any { entry ->
                        entry.station.id == GASOLINE_ONLY_STATION_ID
                    }
                }
                .stations
                .single { entry -> entry.station.id == GASOLINE_ONLY_STATION_ID }
                .station
        }
        stationRepository.updateWatchState(station = station, watched = true)
        station
    }

    private class RecordingExternalMapLauncher : ExternalMapLauncher {
        val providers = mutableListOf<MapProvider>()

        override fun open(
            provider: MapProvider,
            stationName: String,
            originLatitude: Double?,
            originLongitude: Double?,
            latitude: Double,
            longitude: Double,
        ): ExternalMapLaunchResult {
            providers += provider
            return ExternalMapLaunchResult.Opened
        }
    }

    private companion object {
        const val GASOLINE_ONLY_STATION_ID = "DEMO-ETC-001"
    }
}
