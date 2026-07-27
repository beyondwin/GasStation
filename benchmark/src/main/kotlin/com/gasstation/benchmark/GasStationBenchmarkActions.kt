package com.gasstation.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.gasstation.demo"

private const val WAIT_TIMEOUT_MS = 20_000L
private const val TARGET_ACTIVITY = "com.gasstation.MainActivity"
private const val COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"
private const val FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
private const val REFRESH_ACTION_DESCRIPTION = "새로고침"
private const val REFRESH_RAIL_TITLE = "가격 갱신 중"
private const val BOTTOM_NAV_WATCHLIST_TAG = "bottom-nav-watchlist"
private const val STATION_LIST_WATCH_TOGGLE_TAG = "station-list-watch-toggle"
private const val WATCHLIST_CARD_TAG = "watchlist-card"

internal fun MacrobenchmarkScope.launchStationList() {
    grantLocationPermissions()
    pressHome()
    startGasStationActivityAndWait()
    waitForStationListContent()
    waitForRefreshRailGone()
}

internal fun MacrobenchmarkScope.startGasStationActivityAndWait() {
    startActivityAndWait { intent ->
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
    }
}

internal fun MacrobenchmarkScope.waitForRefreshRailGone() {
    device.wait(Until.gone(By.text(REFRESH_RAIL_TITLE)), WAIT_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.grantLocationPermissions() {
    device.executeShellCommand("pm grant $TARGET_PACKAGE $COARSE_LOCATION_PERMISSION")
    device.executeShellCommand("pm grant $TARGET_PACKAGE $FINE_LOCATION_PERMISSION")
}

internal fun MacrobenchmarkScope.waitForStationListContent(): UiObject2 =
    waitForObject(
        selector = resourceId(STATION_LIST_WATCH_TOGGLE_TAG),
        label = "station-list watch toggle resource id '$STATION_LIST_WATCH_TOGGLE_TAG'",
    )

internal fun MacrobenchmarkScope.refreshStationList() {
    waitForObject(
        selector = By.desc(REFRESH_ACTION_DESCRIPTION),
        label = "refresh action '$REFRESH_ACTION_DESCRIPTION'",
    ).click()
    waitForRefreshRailGone()
    waitForStationListContent()
}

internal fun MacrobenchmarkScope.scrollStationList() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.28f).toInt(),
        16,
    )
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openWatchlistWithSavedStation() {
    clickStable(
        selector = resourceId(STATION_LIST_WATCH_TOGGLE_TAG),
        label = "station-list watch toggle resource id '$STATION_LIST_WATCH_TOGGLE_TAG'",
    )
    clickStable(
        selector = resourceId(BOTTOM_NAV_WATCHLIST_TAG),
        label = "watchlist bottom navigation resource id '$BOTTOM_NAV_WATCHLIST_TAG'",
    )
    waitForObject(
        selector = resourceId(WATCHLIST_CARD_TAG),
        label = "watchlist card resource id '$WATCHLIST_CARD_TAG'",
    )
}

private fun resourceId(tag: String): BySelector = By.res(tag)

private fun MacrobenchmarkScope.clickStable(selector: BySelector, label: String) {
    var lastError: Throwable? = null
    repeat(3) {
        try {
            waitForObject(selector, label).click()
            device.waitForIdle()
            return
        } catch (error: StaleObjectException) {
            lastError = error
            device.waitForIdle()
        }
    }
    throw IllegalStateException(
        "Stale UI object kept invalidating while clicking $label",
        lastError,
    )
}

private fun MacrobenchmarkScope.waitForObject(selector: BySelector, label: String): UiObject2 {
    check(device.wait(Until.hasObject(selector), WAIT_TIMEOUT_MS)) {
        "Timed out after ${WAIT_TIMEOUT_MS}ms waiting for $label"
    }
    return requireNotNull(device.findObject(selector)) {
        "UiAutomator reported $label but findObject returned null"
    }
}
