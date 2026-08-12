package com.gasstation.data.station

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class WatchIntentTicket(val stationId: String, val generation: Long) {
    private var entryIdentity: WatchIntentEntryIdentity? = null

    internal constructor(
        stationId: String,
        generation: Long,
        entryIdentity: WatchIntentEntryIdentity,
    ) : this(stationId, generation) {
        this.entryIdentity = entryIdentity
    }

    internal fun wasIssuedBy(entryIdentity: WatchIntentEntryIdentity): Boolean = this.entryIdentity === entryIdentity
}

internal class WatchIntentEntryIdentity

internal sealed interface LatestWatchCommitResult<out T> {
    data class Committed<T>(val value: T) : LatestWatchCommitResult<T>

    data object Superseded : LatestWatchCommitResult<Nothing>
}

internal class LatestWatchIntentGate {
    private val registryMutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun begin(stationId: String): WatchIntentTicket {
        val entry = registryMutex.withLock {
            entries.getOrPut(stationId) { Entry() }.also { it.participants += 1 }
        }

        return try {
            entry.commitMutex.withLock {
                val generation = entry.nextGeneration++
                entry.latestGeneration = generation
                entry.activeGenerations += generation
                WatchIntentTicket(
                    stationId = stationId,
                    generation = generation,
                    entryIdentity = entry.identity,
                )
            }
        } catch (throwable: Throwable) {
            withContext(NonCancellable) {
                releaseParticipant(stationId, entry)
            }
            throw throwable
        }
    }

    suspend fun <T> commitIfLatest(ticket: WatchIntentTicket, block: suspend () -> T): LatestWatchCommitResult<T> {
        val entry = registryMutex.withLock {
            entries[ticket.stationId]?.takeIf { ticket.wasIssuedBy(it.identity) }
        } ?: return LatestWatchCommitResult.Superseded

        return entry.commitMutex.withLock {
            if (ticket.generation !in entry.activeGenerations || ticket.generation != entry.latestGeneration) {
                LatestWatchCommitResult.Superseded
            } else {
                LatestWatchCommitResult.Committed(block())
            }
        }
    }

    suspend fun complete(ticket: WatchIntentTicket) {
        val entry = registryMutex.withLock {
            entries[ticket.stationId]?.takeIf { ticket.wasIssuedBy(it.identity) }
        } ?: return
        val completed = entry.commitMutex.withLock {
            entry.activeGenerations.remove(ticket.generation)
        }
        if (completed) {
            releaseParticipant(ticket.stationId, entry)
        }
    }

    private suspend fun releaseParticipant(stationId: String, entry: Entry) {
        registryMutex.withLock {
            entry.participants -= 1
            if (entry.participants == 0) {
                entries.remove(stationId, entry)
            }
        }
    }

    private class Entry {
        val identity = WatchIntentEntryIdentity()
        val commitMutex = Mutex()
        val activeGenerations = mutableSetOf<Long>()
        var nextGeneration = 1L
        var latestGeneration = 0L
        var participants = 0
    }
}
