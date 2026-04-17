package com.gasstation.domain.settings

import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeUserPreferences(): Flow<UserPreferences>

    suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences)
}
