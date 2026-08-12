package com.gasstation.data.station

import com.gasstation.core.model.FuelType
import com.gasstation.domain.station.model.StationQueryCacheKey
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
class LatestRefreshGateTest {
    private val key = cacheKey(latitudeBucket = 100)

    @Test
    fun `older generation cannot commit after newer generation registers`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val older = gate.begin(key)
        val newer = gate.begin(key)
        var olderBlockExecuted = false

        val result = gate.commitIfLatest(older) {
            olderBlockExecuted = true
        }

        assertIs<LatestCommitResult.Superseded>(result)
        assertFalse(olderBlockExecuted)
        assertIs<LatestCommitResult.Committed<Unit>>(gate.commitIfLatest(newer) {})
        gate.complete(newer)
        gate.complete(older)
    }

    @Test
    fun `different cache keys commit independently`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val first = gate.begin(key)
        val second = gate.begin(cacheKey(latitudeBucket = 200))
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
        assertEquals("second", assertIs<LatestCommitResult.Committed<String>>(secondCommit.await()).value)
        releaseFirst.complete(Unit)
        firstCommit.join()
        gate.complete(second)
        gate.complete(first)
    }

    @Test
    fun `commit block is serialized with same key registration`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val first = gate.begin(key)
        val commitEntered = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val commit = launch {
            gate.commitIfLatest(first) {
                commitEntered.complete(Unit)
                releaseCommit.await()
            }
        }

        commitEntered.await()
        val secondRegistration = async { gate.begin(key) }
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
    fun `cancelled waiter does not execute its commit block`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val ticket = gate.begin(key)
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
    fun `newer completion does not remove tombstone while older generation remains`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val older = gate.begin(key)
        val newer = gate.begin(key)
        gate.complete(newer)

        val newest = gate.begin(key)
        var olderBlockExecuted = false
        val olderResult = gate.commitIfLatest(older) {
            olderBlockExecuted = true
        }

        assertEquals(newer.generation + 1, newest.generation)
        assertIs<LatestCommitResult.Superseded>(olderResult)
        assertFalse(olderBlockExecuted)
        gate.complete(newest)
        gate.complete(older)
    }

    @Test
    fun `entry is released only after every generation completes`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val older = gate.begin(key)
        val newer = gate.begin(key)

        gate.complete(newer)
        val retainedEntryTicket = gate.begin(key)
        assertEquals(3L, retainedEntryTicket.generation)
        gate.complete(retainedEntryTicket)
        gate.complete(older)

        val newEntryTicket = gate.begin(key)
        assertEquals(1L, newEntryTicket.generation)
        gate.complete(newEntryTicket)
    }

    @Test
    fun `cancelled registration releases its reservation without removing active entry`() = runTest(timeout = 10.seconds) {
        val gate = LatestRefreshGate()
        val active = gate.begin(key)
        val commitEntered = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val commit = launch {
            gate.commitIfLatest(active) {
                commitEntered.complete(Unit)
                releaseCommit.await()
            }
        }
        commitEntered.await()
        val cancelledRegistration = async { gate.begin(key) }
        runCurrent()

        cancelledRegistration.cancelAndJoin()
        releaseCommit.complete(Unit)
        commit.join()
        gate.complete(active)

        val afterCleanup = gate.begin(key)
        assertEquals(1L, afterCleanup.generation)
        gate.complete(afterCleanup)
    }

    private fun cacheKey(latitudeBucket: Int) = StationQueryCacheKey(
        latitudeBucket = latitudeBucket,
        longitudeBucket = 200,
        radiusMeters = 3_000,
        fuelType = FuelType.GASOLINE,
    )
}
