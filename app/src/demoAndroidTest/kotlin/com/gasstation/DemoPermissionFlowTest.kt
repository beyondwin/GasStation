package com.gasstation

import android.os.Build
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
import com.gasstation.test.DeviceFailureArtifactRule
import com.gasstation.test.DeviceFailureContext
import com.gasstation.test.DevicePrSmoke
import com.gasstation.test.PermissionButton
import com.gasstation.test.PermissionUiContract
import com.gasstation.test.PermissionUiResource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.gasstation.feature.stationlist.R as StationListR

private const val PERMISSION_GUIDANCE_TAG = "station-list-permission-guidance"
private const val WATCH_TOGGLE_TAG = "station-list-watch-toggle"
private const val PERMISSION_DIALOG_TIMEOUT_MS = 15_000L

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoPermissionFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val failureArtifacts = DeviceFailureArtifactRule()

    @DevicePrSmoke
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

    @DevicePrSmoke
    @Test
    fun b_denyingExplicitPermissionRequest_keepsGuidanceWithoutNearbyContent() {
        rule.onNodeWithText(stationListString(StationListR.string.station_list_permission_action)).performClick()
        permissionButton(PermissionButton.DENY).click()

        rule.onNodeWithTag(PERMISSION_GUIDANCE_TAG).assertExists()
        assertNoNearbyContent()
    }

    @DevicePrSmoke
    @Test
    fun c_grantingExplicitPermissionRequest_revealsFixedDemoNearbyContent() {
        rule.onNodeWithText(stationListString(StationListR.string.station_list_permission_action)).performClick()
        permissionButton(PermissionButton.ALLOW).click()

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

    private fun permissionButton(button: PermissionButton): UiObject2 {
        val device = device()
        val candidates = PermissionUiContract.candidates(Build.VERSION.SDK_INT, button)
        val deadline = SystemClock.uptimeMillis() + PERMISSION_DIALOG_TIMEOUT_MS
        var matches: List<Pair<PermissionUiResource, UiObject2>> = emptyList()
        while (SystemClock.uptimeMillis() < deadline) {
            matches =
                candidates.flatMap { candidate ->
                    device.findObjects(By.res(candidate.packageName, candidate.resourceName))
                        .filter { objectUnderTest ->
                            objectUnderTest.isEnabled && !objectUnderTest.visibleBounds.isEmpty
                        }
                        .map { objectUnderTest -> candidate to objectUnderTest }
                }
            if (matches.isNotEmpty()) break
            SystemClock.sleep(100)
        }
        check(matches.size == 1) {
            "Expected exactly one visible enabled permission button for SDK ${Build.VERSION.SDK_INT}; " +
                "attempted=$candidates matches=${matches.map { it.first }}"
        }
        DeviceFailureContext.recordPermissionSelection(matches.single().first)
        return matches.single().second
    }

    private fun hasPermissionDialog(): Boolean {
        val device = device()
        return (
            PermissionUiContract.candidates(Build.VERSION.SDK_INT, PermissionButton.ALLOW) +
                PermissionUiContract.candidates(Build.VERSION.SDK_INT, PermissionButton.DENY)
            )
            .any { candidate ->
                device.findObjects(By.res(candidate.packageName, candidate.resourceName))
                    .any { objectUnderTest -> objectUnderTest.isEnabled && !objectUnderTest.visibleBounds.isEmpty }
            }
    }

    private fun stationListString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}
