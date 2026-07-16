package com.gasstation.navigation

import com.gasstation.core.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationTopLevelNavigationTest {

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
        assertTrue(TOP_LEVEL_NAVIGATION_POLICY.launchSingleTop)
        assertTrue(TOP_LEVEL_NAVIGATION_POLICY.restoreState)
        assertTrue(TOP_LEVEL_NAVIGATION_POLICY.saveState)
    }
}
