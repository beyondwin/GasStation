package com.gasstation.feature.settings

import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdateMapProviderUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `settings exposes loading without default selections before first emission`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = ControllableSettingsRepository()
            val viewModel = settingsViewModel(repository)

            advanceUntilIdle()

            assertEquals(SettingsUiState.Loading, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful selection emits completion after committed state is ready`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = ControllableSettingsRepository(UserPreferences.default())
            val viewModel = settingsViewModel(repository)
            advanceUntilIdle()

            val effects = mutableListOf<SettingsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(effects::add)
            }
            viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))
            advanceUntilIdle()

            val ready = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(SortOrder.PRICE, ready.preferences.sortOrder)
            assertEquals(null, ready.savingSection)
            assertEquals(listOf(SettingsEffect.SelectionSaved(SettingsSection.SortOrder)), effects)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed selection keeps prior value and emits failure without completion`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = ControllableSettingsRepository(
                initial = UserPreferences.default(),
                updateFailure = IllegalStateException("write failed"),
            )
            val viewModel = settingsViewModel(repository)
            advanceUntilIdle()

            val effects = mutableListOf<SettingsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(effects::add)
            }
            viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
            advanceUntilIdle()

            val ready = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(FuelType.GASOLINE, ready.preferences.fuelType)
            assertEquals(listOf(SettingsEffect.SaveFailed), effects)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful selection produced without collector is delivered once after reattachment`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = ControllableSettingsRepository(UserPreferences.default())
            val viewModel = settingsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))
            advanceUntilIdle()

            val firstAttachment = mutableListOf<SettingsEffect>()
            val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(firstAttachment::add)
            }
            runCurrent()

            assertEquals(
                listOf(SettingsEffect.SelectionSaved(SettingsSection.SortOrder)),
                firstAttachment,
            )

            firstCollector.cancel()
            val secondAttachment = mutableListOf<SettingsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(secondAttachment::add)
            }
            runCurrent()

            assertEquals(emptyList<SettingsEffect>(), secondAttachment)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed selection produced without collector is delivered once after reattachment`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = ControllableSettingsRepository(
                initial = UserPreferences.default(),
                updateFailure = IllegalStateException("write failed"),
            )
            val viewModel = settingsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
            advanceUntilIdle()

            val firstAttachment = mutableListOf<SettingsEffect>()
            val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(firstAttachment::add)
            }
            runCurrent()

            assertEquals(listOf(SettingsEffect.SaveFailed), firstAttachment)

            firstCollector.cancel()
            val secondAttachment = mutableListOf<SettingsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(secondAttachment::add)
            }
            runCurrent()

            assertEquals(emptyList<SettingsEffect>(), secondAttachment)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `preference writes admit only one immediate action while first is suspended`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = ControllableSettingsRepository(UserPreferences.default())
            val suspendedWrite = repository.suspendWrites()
            val viewModel = settingsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))
            assertEquals(
                SettingsSection.SortOrder,
                (viewModel.uiState.value as SettingsUiState.Ready).savingSection,
            )
            viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
            runCurrent()

            assertEquals(1, repository.updateCallCount)
            suspendedWrite.release.complete(Unit)
            advanceUntilIdle()

            val afterFirst = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(SortOrder.PRICE, afterFirst.preferences.sortOrder)
            assertEquals(FuelType.GASOLINE, afterFirst.preferences.fuelType)
            assertEquals(null, afterFirst.savingSection)

            viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
            advanceUntilIdle()

            val afterSecond = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(2, repository.updateCallCount)
            assertEquals(FuelType.DIESEL, afterSecond.preferences.fuelType)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `changing sort order persists selection`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeSettingsRepository(UserPreferences.default())
            val viewModel = SettingsViewModel(
                observeUserPreferences = ObserveUserPreferencesUseCase(repository),
                updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository),
                updateFuelType = UpdateFuelTypeUseCase(repository),
                updateSearchRadius = UpdateSearchRadiusUseCase(repository),
                updateBrandFilter = UpdateBrandFilterUseCase(repository),
                updateMapProvider = UpdateMapProviderUseCase(repository),
            )
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))
            advanceUntilIdle()

            val ready = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(SortOrder.PRICE, ready.preferences.sortOrder)
            assertEquals(SortOrder.PRICE, repository.current.sortOrder)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `changing brand filter persists special legacy-mapped selection`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeSettingsRepository(UserPreferences.default())
            val viewModel = SettingsViewModel(
                observeUserPreferences = ObserveUserPreferencesUseCase(repository),
                updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository),
                updateFuelType = UpdateFuelTypeUseCase(repository),
                updateSearchRadius = UpdateSearchRadiusUseCase(repository),
                updateBrandFilter = UpdateBrandFilterUseCase(repository),
                updateMapProvider = UpdateMapProviderUseCase(repository),
            )
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.BrandFilterSelected(BrandFilter.ETC))
            advanceUntilIdle()

            val ready = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(BrandFilter.ETC, ready.preferences.brandFilter)
            assertEquals(BrandFilter.ETC, repository.current.brandFilter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `changing fuel type persists selection`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeSettingsRepository(UserPreferences.default())
            val viewModel = SettingsViewModel(
                observeUserPreferences = ObserveUserPreferencesUseCase(repository),
                updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository),
                updateFuelType = UpdateFuelTypeUseCase(repository),
                updateSearchRadius = UpdateSearchRadiusUseCase(repository),
                updateBrandFilter = UpdateBrandFilterUseCase(repository),
                updateMapProvider = UpdateMapProviderUseCase(repository),
            )
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
            advanceUntilIdle()

            val ready = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(FuelType.DIESEL, ready.preferences.fuelType)
            assertEquals(FuelType.DIESEL, repository.current.fuelType)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `changing radius and map provider persists both selections`() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeSettingsRepository(UserPreferences.default())
            val viewModel = SettingsViewModel(
                observeUserPreferences = ObserveUserPreferencesUseCase(repository),
                updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository),
                updateFuelType = UpdateFuelTypeUseCase(repository),
                updateSearchRadius = UpdateSearchRadiusUseCase(repository),
                updateBrandFilter = UpdateBrandFilterUseCase(repository),
                updateMapProvider = UpdateMapProviderUseCase(repository),
            )
            advanceUntilIdle()

            viewModel.onAction(SettingsAction.SearchRadiusSelected(SearchRadius.KM_5))
            advanceUntilIdle()
            viewModel.onAction(SettingsAction.MapProviderSelected(MapProvider.NAVER_MAP))
            advanceUntilIdle()

            val ready = viewModel.uiState.value as SettingsUiState.Ready
            assertEquals(SearchRadius.KM_5, ready.preferences.searchRadius)
            assertEquals(MapProvider.NAVER_MAP, ready.preferences.mapProvider)
            assertEquals(SearchRadius.KM_5, repository.current.searchRadius)
            assertEquals(MapProvider.NAVER_MAP, repository.current.mapProvider)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private fun settingsViewModel(repository: SettingsRepository) = SettingsViewModel(
    observeUserPreferences = ObserveUserPreferencesUseCase(repository),
    updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository),
    updateFuelType = UpdateFuelTypeUseCase(repository),
    updateSearchRadius = UpdateSearchRadiusUseCase(repository),
    updateBrandFilter = UpdateBrandFilterUseCase(repository),
    updateMapProvider = UpdateMapProviderUseCase(repository),
)

private class ControllableSettingsRepository(initial: UserPreferences? = null, private val updateFailure: Exception? = null) :
    SettingsRepository {
    private val state = MutableSharedFlow<UserPreferences>(replay = 1).apply {
        initial?.let(::tryEmit)
    }

    private var current = initial
    private var suspendedWrite: SuspendedSettingsWrite? = null

    var updateCallCount: Int = 0
        private set

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences {
        updateCallCount += 1
        updateFailure?.let { throw it }
        suspendedWrite?.let { write ->
            write.started.complete(Unit)
            write.release.await()
        }
        return transform(requireNotNull(current)).also { committed ->
            current = committed
            state.emit(committed)
        }
    }

    fun suspendWrites(): SuspendedSettingsWrite = SuspendedSettingsWrite(
        started = CompletableDeferred(),
        release = CompletableDeferred(),
    ).also { suspendedWrite = it }
}

private data class SuspendedSettingsWrite(val started: CompletableDeferred<Unit>, val release: CompletableDeferred<Unit>)

private class FakeSettingsRepository(initial: UserPreferences) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    val current: UserPreferences
        get() = state.value

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences {
        state.value = transform(state.value)
        return state.value
    }
}
