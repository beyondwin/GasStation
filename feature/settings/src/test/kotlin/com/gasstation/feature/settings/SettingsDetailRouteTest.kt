package com.gasstation.feature.settings

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDetailRouteTest {
    @Test
    fun `failure followed immediately by retry success navigates once without stale snackbar`() = runTest {
        val effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
        val snackbarHostState = SnackbarHostState()
        var navigationCount = 0
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            collectSettingsDetailEffects(
                effects = effects,
                section = SettingsSection.FuelType,
                snackbarHostState = snackbarHostState,
                saveFailedMessage = "저장 실패",
                onBackClick = { navigationCount += 1 },
            )
        }

        effects.emit(SettingsEffect.SaveFailed)
        runCurrent()
        assertEquals("저장 실패", snackbarHostState.currentSnackbarData?.visuals?.message)

        effects.emit(SettingsEffect.SelectionSaved(SettingsSection.FuelType))
        runCurrent()

        assertEquals(1, navigationCount)
        assertNull(snackbarHostState.currentSnackbarData)
        collectionJob.cancel()
    }
}
