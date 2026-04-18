package com.gasstation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.gasstation.feature.settings.SettingsDetailRoute
import com.gasstation.feature.watchlist.WatchlistRoute
import com.gasstation.feature.settings.SettingsRoute
import com.gasstation.feature.settings.SettingsSection
import com.gasstation.feature.stationlist.StationListRoute
import com.gasstation.map.ExternalMapLauncher

@Composable
fun GasStationNavHost(
    externalMapLauncher: ExternalMapLauncher,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = GasStationDestination.StationList.route,
    ) {
        composable(GasStationDestination.StationList.route) {
            StationListRoute(
                onSettingsClick = { navController.navigate(GasStationDestination.Settings.route) },
                onWatchlistClick = { coordinates ->
                    navController.navigate(GasStationDestination.Watchlist.createRoute(coordinates))
                },
                onOpenExternalMap = { effect ->
                    externalMapLauncher.open(
                        provider = effect.provider,
                        stationName = effect.stationName,
                        latitude = effect.latitude,
                        longitude = effect.longitude,
                    )
                },
            )
        }
        composable(GasStationDestination.Settings.route) {
            SettingsRoute(
                onCloseClick = { navController.popBackStack() },
                onSectionClick = { section ->
                    navController.navigate(GasStationDestination.SettingsDetail.createRoute(section))
                },
            )
        }
        composable(
            route = GasStationDestination.SettingsDetail.route,
            arguments = listOf(
                navArgument(GasStationDestination.SettingsDetail.sectionArg) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val routeSegment = requireNotNull(
                backStackEntry.arguments?.getString(GasStationDestination.SettingsDetail.sectionArg),
            )
            val section = SettingsSection.requireFromRouteSegment(routeSegment)
            val settingsBackStackEntry = remember(backStackEntry) {
                navController.getBackStackEntry(GasStationDestination.Settings.route)
            }

            SettingsDetailRoute(
                section = section,
                onBackClick = { navController.popBackStack() },
                viewModelStoreOwner = settingsBackStackEntry,
            )
        }
        composable(GasStationDestination.Watchlist.route) {
            WatchlistRoute()
        }
    }
}
