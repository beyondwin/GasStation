package com.gasstation.domain.settings.usecase

import com.gasstation.core.model.BrandFilter
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject

public class UpdateBrandFilterUseCase @Inject public constructor(private val settingsRepository: SettingsRepository) {
    public suspend operator fun invoke(brandFilter: BrandFilter): UserPreferences = settingsRepository.updateUserPreferences { current ->
        current.copy(brandFilter = brandFilter)
    }
}
