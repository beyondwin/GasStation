package com.gasstation.feature.stationlist

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.TogglePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

internal class SettingsUseCaseTestFixture(initialPreferences: UserPreferences? = UserPreferences.default()) {
    private val state = MutableSharedFlow<UserPreferences>(replay = 1)
    private val failure = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    init {
        initialPreferences?.let(state::tryEmit)
    }

    private val repository = object : SettingsRepository {
        override fun observeUserPreferences(): Flow<UserPreferences> = merge(
            state,
            failure.map { throwable -> throw throwable },
        )

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences {
            val current = state.replayCache.single()
            val updated = transform(current)
            state.emit(updated)
            return updated
        }
    }

    val observeUserPreferences = ObserveUserPreferencesUseCase(repository)
    val updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository)
    val togglePreferredSortOrder = TogglePreferredSortOrderUseCase(repository)
    val updateSearchRadius = UpdateSearchRadiusUseCase(repository)
    val updateFuelType = UpdateFuelTypeUseCase(repository)
    val updateBrandFilter = UpdateBrandFilterUseCase(repository)

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        state.tryEmit(transform(currentPreferences))
    }

    fun emit(preferences: UserPreferences) {
        state.tryEmit(preferences)
    }

    fun fail(throwable: Throwable) {
        failure.tryEmit(throwable)
    }

    val currentPreferences: UserPreferences
        get() = state.replayCache.single()
}
