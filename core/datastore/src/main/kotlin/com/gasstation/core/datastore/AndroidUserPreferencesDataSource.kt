package com.gasstation.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

class AndroidUserPreferencesDataSource(
    private val dataStore: DataStore<StoredUserPreferences>,
) : UserPreferencesDataSource {
    override val userPreferences: Flow<StoredUserPreferences> = dataStore.data

    override suspend fun update(transform: (StoredUserPreferences) -> StoredUserPreferences) {
        dataStore.updateData { current ->
            transform(current)
        }
    }
}
