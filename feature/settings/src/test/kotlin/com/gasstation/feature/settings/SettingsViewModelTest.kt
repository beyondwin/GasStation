package com.gasstation.feature.settings

import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdateMapProviderUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

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

            viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))
            advanceUntilIdle()

            assertEquals(SortOrder.PRICE, viewModel.uiState.value.sortOrder)
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

            viewModel.onAction(SettingsAction.BrandFilterSelected(BrandFilter.ETC))
            advanceUntilIdle()

            assertEquals(BrandFilter.ETC, viewModel.uiState.value.brandFilter)
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

            viewModel.onAction(SettingsAction.FuelTypeSelected(FuelType.DIESEL))
            advanceUntilIdle()

            assertEquals(FuelType.DIESEL, viewModel.uiState.value.fuelType)
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

            viewModel.onAction(SettingsAction.SearchRadiusSelected(SearchRadius.KM_5))
            viewModel.onAction(SettingsAction.MapProviderSelected(MapProvider.NAVER_MAP))
            advanceUntilIdle()

            assertEquals(SearchRadius.KM_5, viewModel.uiState.value.searchRadius)
            assertEquals(MapProvider.NAVER_MAP, viewModel.uiState.value.mapProvider)
            assertEquals(SearchRadius.KM_5, repository.current.searchRadius)
            assertEquals(MapProvider.NAVER_MAP, repository.current.mapProvider)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    val current: UserPreferences
        get() = state.value

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}
