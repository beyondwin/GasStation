package com.gasstation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.settings.usecase.ObserveUserPreferencesUseCase
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
    private val settingsRepository: SettingsRepository,
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
            settingsRepository.updateUserPreferences { current ->
                when (action) {
                    is SettingsAction.SortOrderSelected -> current.copy(sortOrder = action.sortOrder)
                    is SettingsAction.FuelTypeSelected -> current.copy(fuelType = action.fuelType)
                    is SettingsAction.SearchRadiusSelected -> current.copy(searchRadius = action.radius)
                    is SettingsAction.BrandFilterSelected -> current.copy(brandFilter = action.brandFilter)
                    is SettingsAction.MapProviderSelected -> current.copy(mapProvider = action.mapProvider)
                }
            }
        }
    }
}
