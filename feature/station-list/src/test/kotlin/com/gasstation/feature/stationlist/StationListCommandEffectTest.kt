package com.gasstation.feature.stationlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.gasstation.core.designsystem.string.StringResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ko")
class StationListCommandEffectTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `failed head is attempted once during one START activation despite recomposition`() {
        val command = command(1L)
        var revision by mutableIntStateOf(0)
        var commands by mutableStateOf(listOf(command))
        var attempts = 0
        val acknowledgements = mutableListOf<Long>()

        composeRule.setContent {
            revision
            StationListCommandEffect(
                command = commands.firstOrNull(),
                handle = {
                    attempts += 1
                    throw IllegalStateException("failed")
                },
                acknowledge = acknowledgements::add,
            )
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            revision += 1
            commands = commands + command(2L)
        }
        composeRule.waitForIdle()

        assertEquals(1, attempts)
        assertTrue(acknowledgements.isEmpty())
    }

    @Test
    fun `STOP cancels in flight head and next START retries the same id`() {
        val command = command(7L)
        val firstAttemptStarted = CompletableDeferred<Unit>()
        var attempts = 0
        var cancellations = 0
        val acknowledgements = mutableListOf<Long>()

        composeRule.setContent {
            StationListCommandEffect(
                command = command,
                handle = {
                    attempts += 1
                    if (attempts == 1) {
                        firstAttemptStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancellations += 1
                        }
                    }
                },
                acknowledge = acknowledgements::add,
            )
        }
        composeRule.waitUntil { firstAttemptStarted.isCompleted }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()
        assertEquals(1, cancellations)
        assertTrue(acknowledgements.isEmpty())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()

        assertEquals(2, attempts)
        assertEquals(listOf(7L), acknowledgements)
    }

    @Test
    fun `rapid STOP START retries retained head even when intermediate composition is conflated`() {
        val command = command(8L)
        val firstAttemptStarted = CompletableDeferred<Unit>()
        var attempts = 0
        val acknowledgements = mutableListOf<Long>()

        composeRule.setContent {
            StationListCommandEffect(
                command = command,
                handle = {
                    attempts += 1
                    if (attempts == 1) {
                        firstAttemptStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
                acknowledge = acknowledgements::add,
            )
        }
        composeRule.waitUntil { firstAttemptStarted.isCompleted }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        shadowOf(composeRule.activity.mainLooper).idle()
        composeRule.waitForIdle()

        assertEquals(2, attempts)
        assertEquals(listOf(8L), acknowledgements)
    }

    @Test
    fun `second command starts only after first normal completion and acknowledgement`() {
        val first = command(11L)
        val second = command(12L)
        var commands by mutableStateOf(listOf(first, second))
        val releaseFirst = CompletableDeferred<Unit>()
        val handled = mutableListOf<Long>()
        val acknowledgements = mutableListOf<Long>()

        composeRule.setContent {
            StationListCommandEffect(
                command = commands.firstOrNull(),
                handle = { payload ->
                    val id = if (payload == first.payload) first.id else second.id
                    handled += id
                    if (id == first.id) releaseFirst.await()
                },
                acknowledge = { id ->
                    acknowledgements += id
                    if (commands.firstOrNull()?.id == id) {
                        commands = commands.drop(1)
                    }
                },
            )
        }
        composeRule.waitUntil { handled.isNotEmpty() }
        assertEquals(listOf(11L), handled)
        assertTrue(acknowledgements.isEmpty())

        releaseFirst.complete(Unit)
        composeRule.waitUntil { acknowledgements.size == 2 }

        assertEquals(listOf(11L, 12L), handled)
        assertEquals(listOf(11L, 12L), acknowledgements)
        assertTrue(commands.isEmpty())
    }

    private fun command(id: Long) = StationListUiCommand(
        id = id,
        payload = StationListCommandPayload.ShowSnackbar(StringResource.raw("message-$id")),
    )
}
