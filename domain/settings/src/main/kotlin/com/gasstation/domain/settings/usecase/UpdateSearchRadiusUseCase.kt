package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.SearchRadius
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

class UpdateSearchRadiusUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(searchRadius: SearchRadius): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(searchRadius = searchRadius)
    }
}
