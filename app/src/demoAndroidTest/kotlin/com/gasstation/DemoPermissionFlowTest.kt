package com.gasstation

import android.os.SystemClock
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PERMISSION_GUIDANCE_TAG = "station-list-permission-guidance"
private const val WATCH_TOGGLE_TAG = "station-list-watch-toggle"
private const val PERMISSION_CONTROLLER_PACKAGE = "com.android.permissioncontroller"
private const val ALLOW_FOREGROUND_BUTTON = "permission_allow_foreground_only_button"
private const val DENY_BUTTON = "permission_deny_button"
private const val PERMISSION_DIALOG_TIMEOUT_MS = 15_000L

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoPermissionFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a_deniedDemoFirstEntry_staysOnPermissionGuidanceWithoutOpeningAndroidPermissionUi() {
        rule.onNodeWithTag(PERMISSION_GUIDANCE_TAG).assertExists()

        SystemClock.sleep(1_500)

        assertFalse(
            "Fresh denied startup must not open Android permission UI without the explicit CTA",
            hasPermissionDialog(),
        )
        assertNoNearbyContent()
    }

    @Test
    fun b_denyingExplicitPermissionRequest_keepsGuidanceWithoutNearbyContent() {
        rule.onNodeWithText("권한 요청").performClick()
        permissionButton(DENY_BUTTON).click()

        rule.onNodeWithTag(PERMISSION_GUIDANCE_TAG).assertExists()
        assertNoNearbyContent()
    }

    @Test
    fun c_grantingExplicitPermissionRequest_revealsFixedDemoNearbyContent() {
        rule.onNodeWithText("권한 요청").performClick()
        permissionButton(ALLOW_FOREGROUND_BUTTON).click()

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(
                WATCH_TOGGLE_TAG,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("에너지플러스허브 삼방주유소").assertExists()
    }

    private fun assertNoNearbyContent() {
        rule.onAllNodesWithTag(
            WATCH_TOGGLE_TAG,
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    private fun permissionButton(resourceName: String): UiObject2 {
        val device = device()
        return device.wait(
            Until.findObject(By.res(PERMISSION_CONTROLLER_PACKAGE, resourceName)),
            PERMISSION_DIALOG_TIMEOUT_MS,
        ) ?: device.findObject(By.res(resourceName))
            ?: error("Android permission UI button '$resourceName' was not shown")
    }

    private fun hasPermissionDialog(): Boolean {
        val device = device()
        return device.hasObject(By.res(PERMISSION_CONTROLLER_PACKAGE, ALLOW_FOREGROUND_BUTTON)) ||
            device.hasObject(By.res(ALLOW_FOREGROUND_BUTTON))
    }

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}
