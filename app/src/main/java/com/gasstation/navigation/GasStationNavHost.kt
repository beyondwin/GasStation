package com.gasstation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gasstation.core.model.Coordinates
import com.gasstation.feature.settings.SettingsDetailRoute
import com.gasstation.feature.settings.SettingsRoute
import com.gasstation.feature.settings.SettingsSection
import com.gasstation.feature.stationlist.StationListRoute
import com.gasstation.feature.watchlist.WatchlistRoute
import com.gasstation.map.ExternalMapLaunchResult
import com.gasstation.map.ExternalMapLauncher

@Composable
fun GasStationNavHost(externalMapLauncher: ExternalMapLauncher, onStationListFirstContentDrawn: () -> Unit = {}) {
    val navController = rememberNavController()
    var originLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var originLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var lastWatchlistRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val origin = originLatitude?.let { latitude ->
        originLongitude?.let { longitude -> Coordinates(latitude, longitude) }
    }
    val navigationState = TopLevelNavigationState(origin)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val navigateTopLevel: (String) -> Unit = { route ->
        lastWatchlistRoute = navigateTopLevelDestination(
            navController = navController,
            route = route,
            lastWatchlistRoute = lastWatchlistRoute,
        )
    }

    GasStationRootScaffold(
        bottomBar = {
            if (shouldShowBottomBar(currentRoute)) {
                GasStationBottomNavigation(
                    selected = selectedTopLevelDestination(currentRoute),
                    watchlistEnabled = navigationState.watchlistEnabled,
                    onNearby = {
                        navigateTopLevel(GasStationDestination.StationList.route)
                    },
                    onWatchlist = {
                        navigationState.origin?.let { availableOrigin ->
                            navigateTopLevel(
                                GasStationDestination.Watchlist.createRoute(availableOrigin),
                            )
                        }
                    },
                    onSettings = {
                        navigateTopLevel(GasStationDestination.Settings.route)
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = GasStationDestination.StationList.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            gasStationDestinations(
                navController = navController,
                externalMapLauncher = externalMapLauncher,
                onCoordinatesAvailable = { coordinates ->
                    originLatitude = coordinates?.latitude
                    originLongitude = coordinates?.longitude
                },
                onNavigateTopLevel = navigateTopLevel,
                onStationListFirstContentDrawn = onStationListFirstContentDrawn,
            )
        }
    }
}

@Composable
internal fun GasStationRootScaffold(bottomBar: @Composable () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
        ) {
            content(innerPadding)
        }
    }
}

private fun NavGraphBuilder.gasStationDestinations(
    navController: NavHostController,
    externalMapLauncher: ExternalMapLauncher,
    onCoordinatesAvailable: (Coordinates?) -> Unit,
    onNavigateTopLevel: (String) -> Unit,
    onStationListFirstContentDrawn: () -> Unit,
) {
    composable(
        route = GasStationDestination.StationList.route,
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) {
        StationListRoute(
            onCoordinatesAvailable = onCoordinatesAvailable,
            onOpenExternalMap = { effect ->
                externalMapLauncher.open(
                    provider = effect.provider,
                    stationName = effect.stationName,
                    originLatitude = effect.originLatitude,
                    originLongitude = effect.originLongitude,
                    latitude = effect.latitude,
                    longitude = effect.longitude,
                ) != ExternalMapLaunchResult.Failed
            },
            onFirstContentDrawn = onStationListFirstContentDrawn,
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
            onSectionClick = { section ->
                navController.navigate(GasStationDestination.SettingsDetail.createRoute(section))
            },
        )
    }
    composable(
        route = GasStationDestination.SettingsDetail.route,
        arguments = listOf(
            navArgument(GasStationDestination.SettingsDetail.SECTION_ARG) {
                type = NavType.StringType
            },
        ),
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backwardEnterTransition() },
        popExitTransition = { backwardExitTransition() },
    ) { backStackEntry ->
        val routeSegment = requireNotNull(
            backStackEntry.arguments?.getString(GasStationDestination.SettingsDetail.SECTION_ARG),
        )
        val settingsBackStackEntry = remember(backStackEntry) {
            navController.getBackStackEntry(GasStationDestination.Settings.route)
        }

        SettingsDetailRoute(
            section = SettingsSection.requireFromRouteSegment(routeSegment),
            onBackClick = navController::popBackStack,
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
        WatchlistRoute(
            onNavigateNearby = {
                onNavigateTopLevel(GasStationDestination.StationList.route)
            },
        )
    }
}

internal fun navigateTopLevelDestination(navController: NavHostController, route: String, lastWatchlistRoute: String?): String? {
    val nextWatchlistRoute = route.takeIf { it.isConcreteWatchlistRoute() }
    if (nextWatchlistRoute != null && lastWatchlistRoute != null && nextWatchlistRoute != lastWatchlistRoute) {
        navController.clearBackStack(lastWatchlistRoute)
    }

    navController.navigate(route) {
        launchSingleTop = TOP_LEVEL_NAVIGATION_POLICY.launchSingleTop
        restoreState = TOP_LEVEL_NAVIGATION_POLICY.restoreState
        popUpTo(navController.graph.startDestinationId) {
            saveState = TOP_LEVEL_NAVIGATION_POLICY.saveState
        }
    }

    return nextWatchlistRoute ?: lastWatchlistRoute
}

private fun String.isConcreteWatchlistRoute(): Boolean = startsWith("watchlist/") && this != GasStationDestination.Watchlist.route

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
