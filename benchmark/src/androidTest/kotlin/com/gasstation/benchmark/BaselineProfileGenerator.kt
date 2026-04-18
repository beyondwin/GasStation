package com.gasstation.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun collect() = rule.collect(
        packageName = "com.gasstation.demo",
    ) {
        grantLocationPermissions()
        pressHome()
        startActivityAndWait()
        waitForAndClick(description = "새로고침")
        waitForAndClick(description = "관심 비교")
    }
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.grantLocationPermissions() {
    device.executeShellCommand("pm grant com.gasstation.demo android.permission.ACCESS_COARSE_LOCATION")
    device.executeShellCommand("pm grant com.gasstation.demo android.permission.ACCESS_FINE_LOCATION")
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.waitForAndClick(description: String) {
    device.wait(Until.hasObject(By.desc(description)), 5_000)
    requireNotNull(device.findObject(By.desc(description))) {
        "Unable to find UI element with description=$description"
    }.click()
}
