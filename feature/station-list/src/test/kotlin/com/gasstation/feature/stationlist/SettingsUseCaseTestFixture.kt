package com.gasstation.feature.stationlist

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.TogglePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

internal class SettingsUseCaseTestFixture(initialPreferences: UserPreferences? = UserPreferences.default()) {
    private val state = MutableSharedFlow<UserPreferences>(replay = 1)
    private val failure = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    private var suspendedWrite: SuspendedSettingsWrite? = null

    var updateCallCount: Int = 0
        private set

    var observationSubscriptionCount: Int = 0
        private set

    init {
        initialPreferences?.let(state::tryEmit)
    }

    private val repository = object : SettingsRepository {
        override fun observeUserPreferences(): Flow<UserPreferences> = flow {
            observationSubscriptionCount += 1
            emitAll(
                merge(
                    state,
                    failure.map { throwable -> throw throwable },
                ),
            )
        }

        override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences {
            updateCallCount += 1
            suspendedWrite?.let { write ->
                write.started.complete(Unit)
                write.release.await()
            }
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

    fun suspendWrites(): SuspendedSettingsWrite = SuspendedSettingsWrite(
        started = CompletableDeferred(),
        release = CompletableDeferred(),
    ).also { suspendedWrite = it }

    val currentPreferences: UserPreferences
        get() = state.replayCache.single()
}

internal data class SuspendedSettingsWrite(val started: CompletableDeferred<Unit>, val release: CompletableDeferred<Unit>)
