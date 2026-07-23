package com.gasstation.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsDetailRoute(
    section: SettingsSection,
    onBackClick: () -> Unit,
    viewModelStoreOwner: ViewModelStoreOwner,
    viewModel: SettingsViewModel = hiltViewModel(viewModelStoreOwner = viewModelStoreOwner),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.settings_save_failed)

    LaunchedEffect(viewModel, section, saveFailedMessage) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.SelectionSaved -> {
                    if (effect.section == section) onBackClick()
                }

                SettingsEffect.SaveFailed -> {
                    snackbarHostState.showSnackbar(saveFailedMessage)
                }
            }
        }
    }

    when (val state = uiState) {
        SettingsUiState.Loading -> SettingsLoadingScreen()

        SettingsUiState.LoadFailed -> SettingsLoadFailureScreen(
            onRetry = { viewModel.onAction(SettingsAction.RetryLoad) },
        )

        is SettingsUiState.Ready -> SettingsDetailScreen(
            section = section,
            options = state.optionsFor(section),
            isSaving = state.savingSection == section,
            snackbarHostState = snackbarHostState,
            onBackClick = onBackClick,
            onOptionClick = { option ->
                viewModel.onAction(option.action)
            },
        )
    }
}
