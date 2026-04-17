package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import javax.inject.Inject

class ObserveUserPreferencesUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke() = settingsRepository.observeUserPreferences()
}
