package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.MapProvider
import com.gasstation.domain.settings.SettingsRepository
import javax.inject.Inject

class UpdateMapProviderUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(mapProvider: MapProvider) {
        settingsRepository.updateUserPreferences { current ->
            current.copy(mapProvider = mapProvider)
        }
    }
}
