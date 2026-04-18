package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.SortOrder
import javax.inject.Inject

class UpdatePreferredSortOrderUseCase private constructor(
    private val updater: Updater,
) {
    @Inject
    constructor(settingsRepository: SettingsRepository) : this(
        updater = Updater(settingsRepository::updateUserPreferences),
    )

    constructor(updateUserPreferences: suspend ((UserPreferences) -> UserPreferences) -> Unit) : this(
        updater = Updater(updateUserPreferences),
    )

    suspend operator fun invoke(sortOrder: SortOrder) {
        updater.invoke { current ->
            current.copy(sortOrder = sortOrder)
        }
    }

    private fun interface Updater {
        suspend operator fun invoke(transform: (UserPreferences) -> UserPreferences)
    }
}
