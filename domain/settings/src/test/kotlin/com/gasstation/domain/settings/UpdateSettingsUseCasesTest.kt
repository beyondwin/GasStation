package com.gasstation.domain.settings

import app.cash.turbine.test
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.TogglePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdateMapProviderUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateSettingsUseCasesTest {
    @Test
    fun `fuel type use case updates repository state`() = runTest {
        val repository = FakeSettingsRepository(UserPreferences.default())

        UpdateFuelTypeUseCase(repository)(FuelType.DIESEL)

        assertEquals(FuelType.DIESEL, repository.current.fuelType)
    }

    @Test
    fun `radius brand filter and map provider use cases update repository state`() = runTest {
        val repository = FakeSettingsRepository(UserPreferences.default())

        UpdateSearchRadiusUseCase(repository)(SearchRadius.KM_5)
        UpdateBrandFilterUseCase(repository)(BrandFilter.SOL)
        UpdateMapProviderUseCase(repository)(MapProvider.NAVER_MAP)

        assertEquals(SearchRadius.KM_5, repository.current.searchRadius)
        assertEquals(BrandFilter.SOL, repository.current.brandFilter)
        assertEquals(MapProvider.NAVER_MAP, repository.current.mapProvider)
    }

    @Test
    fun `observe and sort order use cases update repository state`() = runTest {
        val repository = FakeSettingsRepository(UserPreferences.default())
        val observeUserPreferences = ObserveUserPreferencesUseCase(repository)
        val updatePreferredSortOrder = UpdatePreferredSortOrderUseCase(repository)

        observeUserPreferences().test {
            assertEquals(UserPreferences.default(), awaitItem())

            updatePreferredSortOrder(SortOrder.PRICE)

            assertEquals(UserPreferences.default().copy(sortOrder = SortOrder.PRICE), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle preferred sort order transforms the repository value atomically`() = runTest {
        val repository = FakeSettingsRepository(
            UserPreferences.default().copy(sortOrder = SortOrder.PRICE),
        )

        val committed = TogglePreferredSortOrderUseCase(repository)()

        assertEquals(SortOrder.DISTANCE, committed.sortOrder)
        assertEquals(committed, repository.current)
    }
}

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
