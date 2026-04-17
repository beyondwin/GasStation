package com.gasstation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gasstation.feature.settings.SettingsRoute
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
            SettingsRoute()
        }
    }
}
