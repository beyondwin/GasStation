package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

public class TogglePreferredSortOrderUseCase @Inject public constructor(private val settingsRepository: SettingsRepository) {
    public suspend operator fun invoke(): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(
            sortOrder = when (current.sortOrder) {
                SortOrder.DISTANCE -> SortOrder.PRICE
                SortOrder.PRICE -> SortOrder.DISTANCE
            },
        )
    }
}
