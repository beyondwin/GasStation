package com.gasstation.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs a block of database writes inside a single transaction so callers can compose multiple
 * DAO operations atomically without depending on Room types directly.
 */
interface DatabaseTransactionRunner {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

class RoomDatabaseTransactionRunner @Inject constructor(private val database: GasStationDatabase) : DatabaseTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = database.withTransaction { block() }
}
