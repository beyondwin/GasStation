package com.gasstation.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun collectHeroJourney() = rule.collect(
        packageName = TARGET_PACKAGE,
    ) {
        grantLocationPermissions()
        pressHome()
        startGasStationActivityAndWait()
        waitForStationListContent()
        refreshStationList()
        scrollStationList()
        openWatchlistWithSavedStation()
    }
}
