package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.MapProvider
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

public class UpdateMapProviderUseCase @Inject public constructor(private val settingsRepository: SettingsRepository) {
    public suspend operator fun invoke(mapProvider: MapProvider): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(mapProvider = mapProvider)
    }
}
