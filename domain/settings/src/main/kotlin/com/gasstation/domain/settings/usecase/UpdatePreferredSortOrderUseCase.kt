package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

public class UpdatePreferredSortOrderUseCase @Inject public constructor(private val settingsRepository: SettingsRepository) {
    public suspend operator fun invoke(sortOrder: SortOrder): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(sortOrder = sortOrder)
    }
}
