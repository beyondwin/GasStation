package com.gasstation.feature.stationlist

import com.gasstation.core.designsystem.string.StringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

class StationListCommandQueueTest {

    @Test
    fun `enqueue retains command without collector`() {
        val queue = StationListCommandQueue()

        val command = queue.enqueue(snackbarPayload("retained"))

        assertEquals(listOf(command), queue.commands.value)
    }

    @Test
    fun `concurrent enqueue publishes every command in serialized id order`() = runTest {
        val queue = StationListCommandQueue()
        val workerCount = 8
        val executor = Executors.newFixedThreadPool(workerCount)
        val dispatcher = executor.asCoroutineDispatcher()
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)

        try {
            val returned = withContext(Dispatchers.Default) {
                val jobs = (1..workerCount).map { payloadNumber ->
                    async(dispatcher) {
                        ready.countDown()
                        start.await()
                        queue.enqueue(snackbarPayload("payload-$payloadNumber"))
                    }
                }
                ready.await()
                start.countDown()
                withTimeout(10.seconds) { jobs.awaitAll() }
            }

            val published = queue.commands.value
            assertEquals(workerCount, published.size)
            assertEquals((1L..workerCount.toLong()).toList(), published.map { it.id })
            assertEquals(workerCount, published.map { it.id }.toSet().size)
            assertEquals(
                returned.associate { it.payload to it.id },
                published.associate { it.payload to it.id },
            )
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `acknowledging non head id is a no op`() {
        val queue = StationListCommandQueue()
        val first = queue.enqueue(snackbarPayload("first"))
        val second = queue.enqueue(snackbarPayload("second"))

        queue.acknowledge(second.id)

        assertEquals(listOf(first, second), queue.commands.value)
    }

    @Test
    fun `acknowledging exact head exposes next command`() {
        val queue = StationListCommandQueue()
        val first = queue.enqueue(snackbarPayload("first"))
        val second = queue.enqueue(snackbarPayload("second"))

        queue.acknowledge(first.id)

        assertEquals(listOf(second), queue.commands.value)
    }

    @Test
    fun `repeated or stale acknowledgement is a no op`() {
        val queue = StationListCommandQueue()
        val first = queue.enqueue(snackbarPayload("first"))
        val second = queue.enqueue(snackbarPayload("second"))

        queue.acknowledge(first.id)
        queue.acknowledge(first.id)
        queue.acknowledge(0L)
        queue.acknowledge(999L)

        assertEquals(listOf(second), queue.commands.value)
    }

    @Test
    fun `a new queue starts empty and owns an independent id sequence`() {
        val firstQueue = StationListCommandQueue()
        val secondQueue = StationListCommandQueue()

        val firstCommand = firstQueue.enqueue(snackbarPayload("first"))

        assertTrue(secondQueue.commands.value.isEmpty())
        assertEquals(1L, firstCommand.id)
        assertEquals(1L, secondQueue.enqueue(snackbarPayload("second")).id)
    }

    private fun snackbarPayload(message: String): StationListCommandPayload =
        StationListCommandPayload.ShowSnackbar(StringResource.raw(message))
}
