package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.FuelType
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

public class UpdateFuelTypeUseCase @Inject public constructor(private val settingsRepository: SettingsRepository) {
    public suspend operator fun invoke(fuelType: FuelType): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(fuelType = fuelType)
    }
}
