package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.station.model.SortOrder
import javax.inject.Inject

class UpdatePreferredSortOrderUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(sortOrder: SortOrder) {
        settingsRepository.updateUserPreferences { current ->
            current.copy(sortOrder = sortOrder)
        }
    }
}
