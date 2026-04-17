package com.gasstation.core.datastore

import androidx.datastore.core.DataStore
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

class AndroidUserPreferencesDataSource(
    private val dataStore: DataStore<UserPreferences>,
) : UserPreferencesDataSource {
    override val userPreferences: Flow<UserPreferences> = dataStore.data

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.updateData { current ->
            transform(current)
        }
    }
}
