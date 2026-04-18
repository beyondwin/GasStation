package com.gasstation.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationListBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartAndOpenWatchlist() = benchmarkRule.measureRepeated(
        packageName = "com.gasstation.demo",
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
        ),
        iterations = 5,
    ) {
        grantLocationPermissions()
        pressHome()
        startActivityAndWait()
        waitForAndClick(description = "관심 비교")
    }
}

private fun MacrobenchmarkScope.grantLocationPermissions() {
    device.executeShellCommand("pm grant com.gasstation.demo android.permission.ACCESS_COARSE_LOCATION")
    device.executeShellCommand("pm grant com.gasstation.demo android.permission.ACCESS_FINE_LOCATION")
}

private fun MacrobenchmarkScope.waitForAndClick(description: String) {
    device.wait(Until.hasObject(By.desc(description)), 5_000)
    requireNotNull(device.findObject(By.desc(description))) {
        "Unable to find UI element with description=$description"
    }.click()
}
