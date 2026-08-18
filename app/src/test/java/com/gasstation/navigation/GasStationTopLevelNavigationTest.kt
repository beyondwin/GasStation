package com.gasstation.navigation

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.MapProvider
import com.gasstation.feature.settings.SettingsSection
import com.gasstation.feature.stationlist.StationListCommandPayload
import com.gasstation.map.ExternalMapLaunchResult
import com.gasstation.map.ExternalMapLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GasStationTopLevelNavigationTest {

    @Test
    fun `external map command forwards every payload field unchanged`() {
        val calls = mutableListOf<List<Any?>>()
        val launcher = object : ExternalMapLauncher {
            override fun open(
                provider: MapProvider,
                stationName: String,
                originLatitude: Double?,
                originLongitude: Double?,
                latitude: Double,
                longitude: Double,
            ): ExternalMapLaunchResult {
                calls += listOf(provider, stationName, originLatitude, originLongitude, latitude, longitude)
                return ExternalMapLaunchResult.StoreOpened
            }
        }
        val command = StationListCommandPayload.OpenExternalMap(
            provider = MapProvider.KAKAO_MAP,
            stationName = "테스트 주유소",
            originLatitude = null,
            originLongitude = null,
            latitude = 37.5123,
            longitude = 127.0456,
        )

        val handled = openExternalMapCommand(launcher, command)

        assertTrue(handled)
        assertEquals(
            listOf(
                listOf(
                    MapProvider.KAKAO_MAP,
                    "테스트 주유소",
                    null,
                    null,
                    37.5123,
                    127.0456,
                ),
            ),
            calls,
        )
    }

    @Test
    fun `external map command reports an unavailable launcher as unhandled`() {
        val launcher = object : ExternalMapLauncher {
            override fun open(
                provider: MapProvider,
                stationName: String,
                originLatitude: Double?,
                originLongitude: Double?,
                latitude: Double,
                longitude: Double,
            ): ExternalMapLaunchResult = ExternalMapLaunchResult.Failed
        }

        val handled = openExternalMapCommand(
            launcher,
            StationListCommandPayload.OpenExternalMap(
                provider = MapProvider.KAKAO_MAP,
                stationName = "테스트 주유소",
                originLatitude = null,
                originLongitude = null,
                latitude = 37.5123,
                longitude = 127.0456,
            ),
        )

        assertFalse(handled)
    }

    @Test
    fun `bottom bar is visible only on top level routes`() {
        assertTrue(shouldShowBottomBar(GasStationDestination.StationList.route))
        assertTrue(shouldShowBottomBar(GasStationDestination.Settings.route))
        assertTrue(shouldShowBottomBar(GasStationDestination.Watchlist.route))
        assertFalse(shouldShowBottomBar(GasStationDestination.SettingsDetail.route))
        assertFalse(shouldShowBottomBar(null))
    }

    @Test
    fun `watchlist destination requires coordinates`() {
        assertFalse(TopLevelNavigationState(origin = null).watchlistEnabled)
        assertTrue(TopLevelNavigationState(Coordinates(37.49, 127.02)).watchlistEnabled)
    }

    @Test
    fun `settings detail selects no top level item`() {
        assertNull(selectedTopLevelDestination(GasStationDestination.SettingsDetail.route))
    }

    @Test
    fun `watchlist selection uses the declared route pattern`() {
        assertEquals(
            TopLevelDestination.Watchlist,
            selectedTopLevelDestination(GasStationDestination.Watchlist.route),
        )
        assertNull(
            selectedTopLevelDestination(
                GasStationDestination.Watchlist.createRoute(Coordinates(37.49, 127.02)),
            ),
        )
    }

    @Test
    fun `top level navigation preserves destination state and avoids duplicate entries`() {
        val navController = createNavController()

        var lastWatchlistRoute = navController.navigateTopLevel(
            route = GasStationDestination.Settings.route,
            lastWatchlistRoute = null,
        )
        navController.currentBackStackEntry!!.savedStateHandle["scroll"] = 31

        lastWatchlistRoute = navController.navigateTopLevel(
            route = GasStationDestination.Settings.route,
            lastWatchlistRoute = lastWatchlistRoute,
        )

        assertEquals(GasStationDestination.Settings.route, navController.currentDestination?.route)
        assertEquals(31, navController.currentBackStackEntry!!.savedStateHandle["scroll"])
        assertNull(lastWatchlistRoute)
        assertTrue(navController.popBackStack())
        assertEquals(GasStationDestination.StationList.route, navController.currentDestination?.route)

        val watchlistRoute = GasStationDestination.Watchlist.createRoute(FIRST_ORIGIN)
        lastWatchlistRoute = navController.navigateTopLevel(watchlistRoute, lastWatchlistRoute)
        navController.currentBackStackEntry!!.savedStateHandle["scroll"] = 47
        lastWatchlistRoute = navController.navigateTopLevel(watchlistRoute, lastWatchlistRoute)
        assertEquals(GasStationDestination.Watchlist.route, navController.currentDestination?.route)
        assertEquals(47, navController.currentBackStackEntry!!.savedStateHandle["scroll"])
        assertEquals(watchlistRoute, lastWatchlistRoute)
        assertTrue(navController.popBackStack())
        assertEquals(GasStationDestination.StationList.route, navController.currentDestination?.route)
    }

    @Test
    fun `switching nearby settings watchlist and settings restores each top level state`() {
        val navController = createNavController()
        val watchlistRoute = GasStationDestination.Watchlist.createRoute(FIRST_ORIGIN)
        navController.currentBackStackEntry!!.savedStateHandle["screen-state"] = "nearby-state"

        var lastWatchlistRoute = navController.navigateTopLevel(
            route = GasStationDestination.Settings.route,
            lastWatchlistRoute = null,
        )
        navController.currentBackStackEntry!!.savedStateHandle["screen-state"] = "settings-state"
        lastWatchlistRoute = navController.navigateTopLevel(
            route = watchlistRoute,
            lastWatchlistRoute = lastWatchlistRoute,
        )
        navController.currentBackStackEntry!!.savedStateHandle["screen-state"] = "watchlist-state"

        lastWatchlistRoute = navController.navigateTopLevel(
            route = GasStationDestination.Settings.route,
            lastWatchlistRoute = lastWatchlistRoute,
        )

        assertEquals(GasStationDestination.Settings.route, navController.currentDestination?.route)
        assertEquals("settings-state", navController.currentBackStackEntry!!.savedStateHandle["screen-state"])
        lastWatchlistRoute = navController.navigateTopLevel(
            route = watchlistRoute,
            lastWatchlistRoute = lastWatchlistRoute,
        )
        assertEquals(GasStationDestination.Watchlist.route, navController.currentDestination?.route)
        assertEquals("watchlist-state", navController.currentBackStackEntry!!.savedStateHandle["screen-state"])
        lastWatchlistRoute = navController.navigateTopLevel(
            route = GasStationDestination.StationList.route,
            lastWatchlistRoute = lastWatchlistRoute,
        )
        assertEquals(GasStationDestination.StationList.route, navController.currentDestination?.route)
        assertEquals("nearby-state", navController.currentBackStackEntry!!.savedStateHandle["screen-state"])
        assertEquals(watchlistRoute, lastWatchlistRoute)
    }

    @Test
    fun `changed watchlist coordinates discard the old saved entry and use latest arguments`() {
        val navController = createNavController()
        val firstRoute = GasStationDestination.Watchlist.createRoute(FIRST_ORIGIN)
        val latestRoute = GasStationDestination.Watchlist.createRoute(LATEST_ORIGIN)

        var lastWatchlistRoute = navController.navigateTopLevel(firstRoute, lastWatchlistRoute = null)
        navController.currentBackStackEntry!!.savedStateHandle["screen-state"] = "old-origin-state"
        lastWatchlistRoute = navController.navigateTopLevel(
            GasStationDestination.Settings.route,
            lastWatchlistRoute,
        )
        lastWatchlistRoute = navController.navigateTopLevel(latestRoute, lastWatchlistRoute)

        assertEquals(GasStationDestination.Watchlist.route, navController.currentDestination?.route)
        assertEquals(LATEST_ORIGIN.latitude.toString(), navController.currentBackStackEntry!!.arguments?.getString("latitude"))
        assertEquals(LATEST_ORIGIN.longitude.toString(), navController.currentBackStackEntry!!.arguments?.getString("longitude"))
        assertNull(navController.currentBackStackEntry!!.savedStateHandle.get<String>("screen-state"))
        assertEquals(latestRoute, lastWatchlistRoute)

        lastWatchlistRoute = navController.navigateTopLevel(
            GasStationDestination.Settings.route,
            lastWatchlistRoute,
        )
        lastWatchlistRoute = navController.navigateTopLevel(firstRoute, lastWatchlistRoute)
        assertEquals(FIRST_ORIGIN.latitude.toString(), navController.currentBackStackEntry!!.arguments?.getString("latitude"))
        assertNull(navController.currentBackStackEntry!!.savedStateHandle.get<String>("screen-state"))
        assertEquals(firstRoute, lastWatchlistRoute)
    }

    @Test
    fun `settings detail hides bottom bar and keeps settings as its view model owner`() {
        val navController = createNavController()
        navController.navigateTopLevel(GasStationDestination.Settings.route, lastWatchlistRoute = null)
        val settingsOwner = navController.getBackStackEntry(GasStationDestination.Settings.route)

        navController.navigate(
            GasStationDestination.SettingsDetail.createRoute(
                SettingsSection.FuelType,
            ),
        )

        assertFalse(shouldShowBottomBar(navController.currentDestination?.route))
        assertEquals(settingsOwner.id, navController.getBackStackEntry(GasStationDestination.Settings.route).id)
        assertTrue(navController.popBackStack())
        assertEquals(GasStationDestination.Settings.route, navController.currentDestination?.route)
        assertTrue(shouldShowBottomBar(navController.currentDestination?.route))
    }

    private fun createNavController(): TestNavHostController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
        setViewModelStore(ViewModelStore())
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = GasStationDestination.StationList.route) {
            composable(GasStationDestination.StationList.route) {}
            composable(GasStationDestination.Settings.route) {}
            composable(
                route = GasStationDestination.SettingsDetail.route,
                arguments = listOf(
                    navArgument(GasStationDestination.SettingsDetail.SECTION_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) {}
            composable(
                route = GasStationDestination.Watchlist.route,
                arguments = listOf(
                    navArgument("latitude") { type = NavType.StringType },
                    navArgument("longitude") { type = NavType.StringType },
                ),
            ) {}
        }
    }

    private fun NavHostController.navigateTopLevel(route: String, lastWatchlistRoute: String?): String? = navigateTopLevelDestination(
        navController = this,
        route = route,
        lastWatchlistRoute = lastWatchlistRoute,
    )

    private companion object {
        val FIRST_ORIGIN = Coordinates(37.49, 127.02)
        val LATEST_ORIGIN = Coordinates(37.51, 127.04)
    }
}
