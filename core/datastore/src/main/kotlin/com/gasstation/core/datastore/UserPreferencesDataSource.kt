package com.gasstation.core.datastore

import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val userPreferences: Flow<StoredUserPreferences>

    suspend fun update(transform: (StoredUserPreferences) -> StoredUserPreferences)
}
