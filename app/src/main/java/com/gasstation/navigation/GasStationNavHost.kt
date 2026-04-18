package com.gasstation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
        composable(
            route = GasStationDestination.StationList.route,
            enterTransition = { forwardEnterTransition() },
            exitTransition = { forwardExitTransition() },
            popEnterTransition = { backwardEnterTransition() },
            popExitTransition = { backwardExitTransition() },
        ) {
            StationListRoute(
                onSettingsClick = { navController.navigate(GasStationDestination.Settings.route) },
                onWatchlistClick = { coordinates ->
                    navController.navigate(GasStationDestination.Watchlist.createRoute(coordinates))
                },
                onOpenExternalMap = { effect ->
                    externalMapLauncher.open(
                        provider = effect.provider,
                        stationName = effect.stationName,
                        originLatitude = effect.originLatitude,
                        originLongitude = effect.originLongitude,
                        latitude = effect.latitude,
                        longitude = effect.longitude,
                    )
                },
            )
        }
        composable(
            route = GasStationDestination.Settings.route,
            enterTransition = { forwardEnterTransition() },
            exitTransition = { forwardExitTransition() },
            popEnterTransition = { backwardEnterTransition() },
            popExitTransition = { backwardExitTransition() },
        ) {
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
            enterTransition = { forwardEnterTransition() },
            exitTransition = { forwardExitTransition() },
            popEnterTransition = { backwardEnterTransition() },
            popExitTransition = { backwardExitTransition() },
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
        composable(
            route = GasStationDestination.Watchlist.route,
            enterTransition = { forwardEnterTransition() },
            exitTransition = { forwardExitTransition() },
            popEnterTransition = { backwardEnterTransition() },
            popExitTransition = { backwardExitTransition() },
        ) {
            WatchlistRoute()
        }
    }
}

private fun forwardEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = 180),
) + slideInHorizontally(
    animationSpec = tween(durationMillis = 220),
    initialOffsetX = { fullWidth -> fullWidth / 10 },
)

private fun forwardExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 140),
) + slideOutHorizontally(
    animationSpec = tween(durationMillis = 180),
    targetOffsetX = { fullWidth -> -fullWidth / 20 },
)

private fun backwardEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = 180),
) + slideInHorizontally(
    animationSpec = tween(durationMillis = 220),
    initialOffsetX = { fullWidth -> -fullWidth / 10 },
)

private fun backwardExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = 140),
) + slideOutHorizontally(
    animationSpec = tween(durationMillis = 180),
    targetOffsetX = { fullWidth -> fullWidth / 20 },
)
