package com.gasstation.data.station

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LatestWatchIntentGateTest {
    @Test
    fun `older same-station generation cannot commit after newer registration`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val older = gate.begin("station-1")
        val newer = gate.begin("station-1")
        var olderBlockExecuted = false

        val result = gate.commitIfLatest(older) {
            olderBlockExecuted = true
        }

        assertIs<LatestWatchCommitResult.Superseded>(result)
        assertFalse(olderBlockExecuted)
        assertIs<LatestWatchCommitResult.Committed<Unit>>(gate.commitIfLatest(newer) {})
        gate.complete(newer)
        gate.complete(older)
    }

    @Test
    fun `different station ids commit independently`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val first = gate.begin("station-1")
        val second = gate.begin("station-2")
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstCommit = launch {
            gate.commitIfLatest(first) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }

        firstEntered.await()
        val secondCommit = async { gate.commitIfLatest(second) { "second" } }
        runCurrent()

        assertTrue(secondCommit.isCompleted)
        assertEquals("second", assertIs<LatestWatchCommitResult.Committed<String>>(secondCommit.await()).value)
        releaseFirst.complete(Unit)
        firstCommit.join()
        gate.complete(second)
        gate.complete(first)
    }

    @Test
    fun `same-station registration waits for active commit then receives next generation`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val first = gate.begin("station-1")
        val commitEntered = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val commit = launch {
            gate.commitIfLatest(first) {
                commitEntered.complete(Unit)
                releaseCommit.await()
            }
        }

        commitEntered.await()
        val secondRegistration = async { gate.begin("station-1") }
        runCurrent()

        assertFalse(secondRegistration.isCompleted)
        releaseCommit.complete(Unit)
        commit.join()
        val second = secondRegistration.await()
        assertEquals(first.generation + 1, second.generation)
        gate.complete(second)
        gate.complete(first)
    }

    @Test
    fun `cancelled commit waiter never executes its block`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val ticket = gate.begin("station-1")
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstCommit = launch {
            gate.commitIfLatest(ticket) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()
        var waitingBlockExecuted = false
        val waitingCommit = launch {
            gate.commitIfLatest(ticket) {
                waitingBlockExecuted = true
            }
        }
        runCurrent()

        waitingCommit.cancelAndJoin()
        releaseFirst.complete(Unit)
        firstCommit.join()

        assertFalse(waitingBlockExecuted)
        gate.complete(ticket)
    }

    @Test
    fun `newer completion retains tombstone while older generation remains`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val older = gate.begin("station-1")
        val newer = gate.begin("station-1")
        gate.complete(newer)

        val newest = gate.begin("station-1")
        var olderBlockExecuted = false
        val olderResult = gate.commitIfLatest(older) {
            olderBlockExecuted = true
        }

        assertEquals(newer.generation + 1, newest.generation)
        assertIs<LatestWatchCommitResult.Superseded>(olderResult)
        assertFalse(olderBlockExecuted)
        gate.complete(newest)
        gate.complete(older)
    }

    @Test
    fun `entry releases only after every generation completes`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val older = gate.begin("station-1")
        val newer = gate.begin("station-1")

        gate.complete(newer)
        val retainedEntryTicket = gate.begin("station-1")
        assertEquals(3L, retainedEntryTicket.generation)
        gate.complete(retainedEntryTicket)
        gate.complete(older)

        val replacement = gate.begin("station-1")
        assertEquals(1L, replacement.generation)
        gate.complete(replacement)
    }

    @Test
    fun `cancelled registration releases reservation without removing active entry`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val active = gate.begin("station-1")
        val commitEntered = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val commit = launch {
            gate.commitIfLatest(active) {
                commitEntered.complete(Unit)
                releaseCommit.await()
            }
        }
        commitEntered.await()
        val cancelledRegistration = async { gate.begin("station-1") }
        runCurrent()

        cancelledRegistration.cancelAndJoin()
        releaseCommit.complete(Unit)
        commit.join()
        gate.complete(active)

        val afterCleanup = gate.begin("station-1")
        assertEquals(1L, afterCleanup.generation)
        gate.complete(afterCleanup)
    }

    @Test
    fun `completed stale ticket cannot commit against replacement entry`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val stale = gate.begin("station-1")
        gate.complete(stale)
        val replacement = gate.begin("station-1")
        var staleBlockExecuted = false

        val staleResult = gate.commitIfLatest(stale) {
            staleBlockExecuted = true
        }

        assertIs<LatestWatchCommitResult.Superseded>(staleResult)
        assertFalse(staleBlockExecuted)
        assertEquals(
            "replacement",
            assertIs<LatestWatchCommitResult.Committed<String>>(
                gate.commitIfLatest(replacement) { "replacement" },
            ).value,
        )
        gate.complete(replacement)
    }

    @Test
    fun `stale complete cannot invalidate replacement entry`() = runTest(timeout = 10.seconds) {
        val gate = LatestWatchIntentGate()
        val stale = gate.begin("station-1")
        gate.complete(stale)
        val replacement = gate.begin("station-1")

        gate.complete(stale)

        assertEquals(
            "replacement",
            assertIs<LatestWatchCommitResult.Committed<String>>(
                gate.commitIfLatest(replacement) { "replacement" },
            ).value,
        )
        gate.complete(replacement)
        val afterReplacement = gate.begin("station-1")
        assertEquals(1L, afterReplacement.generation)
        gate.complete(afterReplacement)
    }
}
