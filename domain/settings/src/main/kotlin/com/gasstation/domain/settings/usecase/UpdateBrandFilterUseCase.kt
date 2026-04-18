package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.station.model.BrandFilter
import javax.inject.Inject

class UpdateBrandFilterUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(brandFilter: BrandFilter) {
        settingsRepository.updateUserPreferences { current ->
            current.copy(brandFilter = brandFilter)
        }
    }
}
