package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserPreferencesUseCase private constructor(
    private val observer: Observer,
) {
    @Inject
    constructor(settingsRepository: SettingsRepository) : this(
        observer = Observer(settingsRepository::observeUserPreferences),
    )

    constructor(observeUserPreferences: () -> Flow<UserPreferences>) : this(
        observer = Observer(observeUserPreferences),
    )

    operator fun invoke() = observer.invoke()

    private fun interface Observer {
        operator fun invoke(): Flow<UserPreferences>
    }
}
