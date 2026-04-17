package com.gasstation.core.datastore

import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val userPreferences: Flow<UserPreferences>

    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}
