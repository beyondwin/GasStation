package com.gasstation.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.gasstation.demo"

private const val WAIT_TIMEOUT_MS = 10_000L
private const val COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"
private const val FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
private const val STATION_TEXT_FRAGMENT = "주유소"
private const val REFRESH_ACTION_DESCRIPTION = "새로고침"
private const val BOOKMARK_ACTION_DESCRIPTION = "북마크"
private const val SAVE_ACTION_DESCRIPTION = "저장"
private const val WATCHLIST_CARD_DESCRIPTION = "관심 주유소 카드"
private const val REFRESH_RAIL_TITLE = "가격 갱신 중"

internal fun MacrobenchmarkScope.launchStationList() {
    grantLocationPermissions()
    pressHome()
    startActivityAndWait()
    waitForStationListContent()
    waitForRefreshRailGone()
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
        selector = By.textContains(STATION_TEXT_FRAGMENT),
        label = "station list content containing '$STATION_TEXT_FRAGMENT'",
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
    waitForObject(
        selector = By.desc(SAVE_ACTION_DESCRIPTION),
        label = "any visible station save action '$SAVE_ACTION_DESCRIPTION'",
    ).click()
    waitForObject(
        selector = By.desc(BOOKMARK_ACTION_DESCRIPTION),
        label = "watchlist action '$BOOKMARK_ACTION_DESCRIPTION'",
    ).click()
    waitForObject(
        selector = By.desc(WATCHLIST_CARD_DESCRIPTION),
        label = "watchlist card '$WATCHLIST_CARD_DESCRIPTION'",
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
