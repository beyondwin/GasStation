package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.core.model.SearchRadius
import javax.inject.Inject

class UpdateSearchRadiusUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(searchRadius: SearchRadius) {
        settingsRepository.updateUserPreferences { current ->
            current.copy(searchRadius = searchRadius)
        }
    }
}
