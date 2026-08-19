package com.gasstation.domain.settings

import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

public interface SettingsRepository {
    public fun observeUserPreferences(): Flow<UserPreferences>

    public suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences
}
