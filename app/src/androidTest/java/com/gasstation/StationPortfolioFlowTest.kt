package com.gasstation

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationPortfolioFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoFlow_can_watch_station_and_open_watchlist() {
        grantLocationPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        grantLocationPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("관심 주유소 토글")
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithContentDescription("관심 주유소 토글")
            .onFirst()
            .performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("관심 비교")
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("관심 비교").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                rule.onNodeWithText("강남역 데모 주유소").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        rule.onNodeWithText("강남역 데모 주유소").fetchSemanticsNode()
    }
}

private fun grantLocationPermission(permission: String) {
    InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand("pm grant com.gasstation.demo $permission")
        .close()
}
