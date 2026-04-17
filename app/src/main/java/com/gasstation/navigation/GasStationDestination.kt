package com.gasstation.navigation

sealed interface GasStationDestination {
    val route: String

    data object StationList : GasStationDestination {
        override val route: String = "station-list"
    }

    data object Settings : GasStationDestination {
        override val route: String = "settings"
    }
}
