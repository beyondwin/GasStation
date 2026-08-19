package com.gasstation.domain.settings.usecase

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

public class ObserveUserPreferencesUseCase private constructor(private val observer: Observer) {
    @Inject
    public constructor(settingsRepository: SettingsRepository) : this(
        observer = Observer(settingsRepository::observeUserPreferences),
    )

    public constructor(observeUserPreferences: () -> Flow<UserPreferences>) : this(
        observer = Observer(observeUserPreferences),
    )

    public operator fun invoke(): Flow<UserPreferences> = observer.invoke()

    private fun interface Observer {
        operator fun invoke(): Flow<UserPreferences>
    }
}
