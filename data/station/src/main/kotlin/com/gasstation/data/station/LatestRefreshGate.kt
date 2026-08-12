package com.gasstation.data.station

import com.gasstation.domain.station.model.StationQueryCacheKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class RefreshTicket(val key: StationQueryCacheKey, val generation: Long) {
    private var entryIdentity: RefreshEntryIdentity? = null

    internal constructor(
        key: StationQueryCacheKey,
        generation: Long,
        entryIdentity: RefreshEntryIdentity,
    ) : this(key, generation) {
        this.entryIdentity = entryIdentity
    }

    internal fun wasIssuedBy(entryIdentity: RefreshEntryIdentity): Boolean = this.entryIdentity === entryIdentity
}

internal class RefreshEntryIdentity

internal sealed interface LatestCommitResult<out T> {
    data class Committed<T>(val value: T) : LatestCommitResult<T>

    data object Superseded : LatestCommitResult<Nothing>
}

internal class LatestRefreshGate {
    private val registryMutex = Mutex()
    private val entries = mutableMapOf<StationQueryCacheKey, Entry>()

    suspend fun begin(key: StationQueryCacheKey): RefreshTicket {
        val entry = registryMutex.withLock {
            entries.getOrPut(key) { Entry() }.also { it.participants += 1 }
        }

        return try {
            entry.commitMutex.withLock {
                val generation = entry.nextGeneration++
                entry.latestGeneration = generation
                entry.activeGenerations += generation
                RefreshTicket(
                    key = key,
                    generation = generation,
                    entryIdentity = entry.identity,
                )
            }
        } catch (throwable: Throwable) {
            withContext(NonCancellable) {
                releaseParticipant(key, entry)
            }
            throw throwable
        }
    }

    suspend fun <T> commitIfLatest(ticket: RefreshTicket, block: suspend () -> T): LatestCommitResult<T> {
        val entry = registryMutex.withLock {
            entries[ticket.key]?.takeIf { ticket.wasIssuedBy(it.identity) }
        }
            ?: return LatestCommitResult.Superseded

        return entry.commitMutex.withLock {
            if (ticket.generation !in entry.activeGenerations || ticket.generation != entry.latestGeneration) {
                LatestCommitResult.Superseded
            } else {
                LatestCommitResult.Committed(block())
            }
        }
    }

    suspend fun complete(ticket: RefreshTicket) {
        val entry = registryMutex.withLock {
            entries[ticket.key]?.takeIf { ticket.wasIssuedBy(it.identity) }
        } ?: return
        val completed = entry.commitMutex.withLock {
            entry.activeGenerations.remove(ticket.generation)
        }
        if (completed) {
            releaseParticipant(ticket.key, entry)
        }
    }

    private suspend fun releaseParticipant(key: StationQueryCacheKey, entry: Entry) {
        registryMutex.withLock {
            entry.participants -= 1
            if (entry.participants == 0) {
                entries.remove(key, entry)
            }
        }
    }

    private class Entry {
        val identity = RefreshEntryIdentity()
        val commitMutex = Mutex()
        val activeGenerations = mutableSetOf<Long>()
        var nextGeneration = 1L
        var latestGeneration = 0L
        var participants = 0
    }
}
