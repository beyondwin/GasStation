package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.station.model.FuelType
import javax.inject.Inject

class UpdateFuelTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(fuelType: FuelType) {
        settingsRepository.updateUserPreferences { current ->
            current.copy(fuelType = fuelType)
        }
    }
}
