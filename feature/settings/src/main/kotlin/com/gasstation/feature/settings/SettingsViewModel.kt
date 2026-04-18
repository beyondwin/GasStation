package com.gasstation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
import com.gasstation.domain.settings.usecase.UpdateBrandFilterUseCase
import com.gasstation.domain.settings.usecase.UpdateFuelTypeUseCase
import com.gasstation.domain.settings.usecase.UpdateMapProviderUseCase
import com.gasstation.domain.settings.usecase.UpdatePreferredSortOrderUseCase
import com.gasstation.domain.settings.usecase.UpdateSearchRadiusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val updateFuelType: UpdateFuelTypeUseCase,
    private val updateSearchRadius: UpdateSearchRadiusUseCase,
    private val updateBrandFilter: UpdateBrandFilterUseCase,
    private val updateMapProvider: UpdateMapProviderUseCase,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState.from(UserPreferences.default()))
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        observeUserPreferences()
            .map(SettingsUiState::from)
            .onEach { mutableUiState.value = it }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SortOrderSelected -> updatePreferredSortOrder(action.sortOrder)
                is SettingsAction.FuelTypeSelected -> updateFuelType(action.fuelType)
                is SettingsAction.SearchRadiusSelected -> updateSearchRadius(action.radius)
                is SettingsAction.BrandFilterSelected -> updateBrandFilter(action.brandFilter)
                is SettingsAction.MapProviderSelected -> updateMapProvider(action.mapProvider)
            }
        }
    }
}
