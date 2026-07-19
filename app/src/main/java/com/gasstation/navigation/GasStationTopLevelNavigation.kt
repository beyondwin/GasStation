package com.gasstation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.gasstation.R
import com.gasstation.core.designsystem.component.GasStationNavigationBar
import com.gasstation.core.designsystem.component.GasStationNavigationBarItem
import com.gasstation.core.model.Coordinates

internal const val BOTTOM_NAV_NEARBY_TAG = "bottom-nav-nearby"
internal const val BOTTOM_NAV_WATCHLIST_TAG = "bottom-nav-watchlist"
internal const val BOTTOM_NAV_SETTINGS_TAG = "bottom-nav-settings"

internal enum class TopLevelDestination {
    Nearby,
    Watchlist,
    Settings,
}

internal data class TopLevelNavigationState(val origin: Coordinates?) {
    val watchlistEnabled: Boolean
        get() = origin != null
}

internal data class TopLevelNavigationPolicy(val launchSingleTop: Boolean, val restoreState: Boolean, val saveState: Boolean)

internal val TOP_LEVEL_NAVIGATION_POLICY = TopLevelNavigationPolicy(
    launchSingleTop = true,
    restoreState = true,
    saveState = true,
)

internal fun shouldShowBottomBar(route: String?): Boolean = route in setOf(
    GasStationDestination.StationList.route,
    GasStationDestination.Watchlist.route,
    GasStationDestination.Settings.route,
)

internal fun selectedTopLevelDestination(route: String?): TopLevelDestination? = when (route) {
    GasStationDestination.StationList.route -> TopLevelDestination.Nearby
    GasStationDestination.Watchlist.route -> TopLevelDestination.Watchlist
    GasStationDestination.Settings.route -> TopLevelDestination.Settings
    else -> null
}

@Composable
internal fun GasStationBottomNavigation(
    selected: TopLevelDestination?,
    watchlistEnabled: Boolean,
    onNearby: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit,
) {
    val nearbyLabel = stringResource(R.string.nav_nearby)
    val watchlistLabel = stringResource(R.string.nav_watchlist)
    val settingsLabel = stringResource(R.string.nav_settings)
    val watchlistDisabledDescription = stringResource(R.string.nav_watchlist_disabled)
    GasStationNavigationBar(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        GasStationNavigationBarItem(
            selected = selected == TopLevelDestination.Nearby,
            onClick = onNearby,
            icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
            modifier = Modifier
                .testTag(BOTTOM_NAV_NEARBY_TAG)
                .semantics { contentDescription = nearbyLabel },
        )
        GasStationNavigationBarItem(
            selected = selected == TopLevelDestination.Watchlist,
            onClick = onWatchlist,
            icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
            enabled = watchlistEnabled,
            modifier = Modifier
                .testTag(BOTTOM_NAV_WATCHLIST_TAG)
                .semantics {
                    contentDescription = watchlistLabel
                    if (!watchlistEnabled) {
                        stateDescription = watchlistDisabledDescription
                    }
                },
        )
        GasStationNavigationBarItem(
            selected = selected == TopLevelDestination.Settings,
            onClick = onSettings,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            modifier = Modifier
                .testTag(BOTTOM_NAV_SETTINGS_TAG)
                .semantics { contentDescription = settingsLabel },
        )
    }
}
