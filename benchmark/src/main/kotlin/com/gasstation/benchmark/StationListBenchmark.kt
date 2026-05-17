package com.gasstation.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationListBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupToFirstContent() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = {
            grantLocationPermissions()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForStationListContent()
    }

    @Test
    fun listScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        setupBlock = {
            launchStationList()
        },
    ) {
        scrollStationList()
    }

    @Test
    fun refreshFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        setupBlock = {
            launchStationList()
        },
    ) {
        refreshStationList()
    }

    @Test
    fun openWatchlistFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        setupBlock = {
            launchStationList()
        },
    ) {
        openWatchlistWithSavedStation()
    }
}
