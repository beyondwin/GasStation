package com.gasstation.feature.stationlist

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class SettingsUseCaseTestFixture(initialPreferences: UserPreferences = UserPreferences.default()) {
    private val state = MutableStateFlow(initialPreferences)
    private val repository = object : SettingsRepository {
        override fun observeUserPreferences(): Flow<UserPreferences> = state

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences {
            state.value = transform(state.value)
            return state.value
        }
    }

    val observeUserPreferences = ObserveUserPreferencesUseCase(repository)
    val updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository)
    val updateSearchRadius = UpdateSearchRadiusUseCase(repository)
    val updateFuelType = UpdateFuelTypeUseCase(repository)
    val updateBrandFilter = UpdateBrandFilterUseCase(repository)

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }

    val currentPreferences: UserPreferences get() = state.value
}
