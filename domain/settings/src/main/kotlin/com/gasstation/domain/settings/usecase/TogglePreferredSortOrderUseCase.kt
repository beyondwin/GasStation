package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

class TogglePreferredSortOrderUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(
            sortOrder = when (current.sortOrder) {
                SortOrder.DISTANCE -> SortOrder.PRICE
                SortOrder.PRICE -> SortOrder.DISTANCE
            },
        )
    }
}
