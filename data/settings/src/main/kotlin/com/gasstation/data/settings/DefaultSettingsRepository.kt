package com.gasstation.data.settings

import com.gasstation.core.datastore.UserPreferencesDataSource
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DefaultSettingsRepository @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : SettingsRepository {
    override fun observeUserPreferences(): Flow<UserPreferences> = dataSource.userPreferences

    override suspend fun updateUserPreferences(
        transform: (UserPreferences) -> UserPreferences,
    ) {
        dataSource.update(transform)
    }
}
