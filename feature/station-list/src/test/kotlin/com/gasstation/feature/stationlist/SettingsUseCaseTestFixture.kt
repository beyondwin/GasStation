package com.gasstation.feature.stationlist

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import kotlinx.coroutines.flow.MutableStateFlow

internal class SettingsUseCaseTestFixture(
    initialPreferences: UserPreferences = UserPreferences.default(),
) {
    private val state = MutableStateFlow(initialPreferences)

    val observeUserPreferences = ObserveUserPreferencesUseCase { state }
    val updatePreferredSortOrder = UpdatePreferredSortOrderUseCase { transform ->
        state.value = transform(state.value)
    }

    val currentPreferences: UserPreferences
        get() = state.value
}
