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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeUserPreferences: ObserveUserPreferencesUseCase,
    private val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase,
    private val updateFuelType: UpdateFuelTypeUseCase,
    private val updateSearchRadius: UpdateSearchRadiusUseCase,
    private val updateBrandFilter: UpdateBrandFilterUseCase,
    private val updateMapProvider: UpdateMapProviderUseCase,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<SettingsEffect>(capacity = Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    private var lastPersistedPreferences: UserPreferences? = null
    private var observationJob: Job? = null

    init {
        observePreferences()
    }

    private fun observePreferences() {
        observationJob?.cancel()
        mutableUiState.value = SettingsUiState.Loading
        observationJob = observeUserPreferences()
            .catch { error ->
                if (error is CancellationException) throw error
                mutableUiState.value = SettingsUiState.LoadFailed
            }
            .onEach { preferences ->
                lastPersistedPreferences = preferences
                val savingSection = (mutableUiState.value as? SettingsUiState.Ready)?.savingSection
                mutableUiState.value = SettingsUiState.Ready(
                    preferences = preferences,
                    savingSection = savingSection,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.RetryLoad -> observePreferences()

            is SettingsAction.SortOrderSelected -> saveSelection(SettingsSection.SortOrder) {
                updatePreferredSortOrder(action.sortOrder)
            }

            is SettingsAction.FuelTypeSelected -> saveSelection(SettingsSection.FuelType) {
                updateFuelType(action.fuelType)
            }

            is SettingsAction.SearchRadiusSelected -> saveSelection(SettingsSection.SearchRadius) {
                updateSearchRadius(action.radius)
            }

            is SettingsAction.BrandFilterSelected -> saveSelection(SettingsSection.BrandFilter) {
                updateBrandFilter(action.brandFilter)
            }

            is SettingsAction.MapProviderSelected -> saveSelection(SettingsSection.MapProvider) {
                updateMapProvider(action.mapProvider)
            }
        }
    }

    private fun saveSelection(section: SettingsSection, update: suspend () -> UserPreferences) {
        val ready = mutableUiState.value as? SettingsUiState.Ready ?: return
        if (ready.savingSection != null) return
        if (!mutableUiState.compareAndSet(ready, ready.copy(savingSection = section))) return

        viewModelScope.launch {
            try {
                val committed = update()
                lastPersistedPreferences = committed
                mutableUiState.value = SettingsUiState.Ready(committed)
                effectChannel.send(SettingsEffect.SelectionSaved(section))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutableUiState.value = SettingsUiState.Ready(
                    lastPersistedPreferences ?: ready.preferences,
                )
                effectChannel.send(SettingsEffect.SaveFailed)
            }
        }
    }
}
