package com.gasstation.navigation

import com.gasstation.core.model.Coordinates
import com.gasstation.feature.settings.SettingsSection

sealed interface GasStationDestination {
    val route: String

    data object StationList : GasStationDestination {
        override val route: String = "station-list"
    }

    data object Settings : GasStationDestination {
        override val route: String = "settings"
    }

    data object SettingsDetail : GasStationDestination {
        const val sectionArg: String = "section"

        override val route: String = "settings/{$sectionArg}"

        fun createRoute(section: SettingsSection): String = "settings/${section.routeSegment}"
    }

    data object Watchlist : GasStationDestination {
        override val route: String = "watchlist/{latitude}/{longitude}"

        fun createRoute(coordinates: Coordinates): String =
            "watchlist/${coordinates.latitude}/${coordinates.longitude}"
    }
}
