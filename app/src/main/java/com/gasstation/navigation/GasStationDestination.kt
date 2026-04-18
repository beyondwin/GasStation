package com.gasstation.navigation

import com.gasstation.core.model.Coordinates

sealed interface GasStationDestination {
    val route: String

    data object StationList : GasStationDestination {
        override val route: String = "station-list"
    }

    data object Settings : GasStationDestination {
        override val route: String = "settings"
    }

    data object Watchlist : GasStationDestination {
        override val route: String = "watchlist/{latitude}/{longitude}"

        fun createRoute(coordinates: Coordinates): String =
            "watchlist/${coordinates.latitude}/${coordinates.longitude}"
    }
}
