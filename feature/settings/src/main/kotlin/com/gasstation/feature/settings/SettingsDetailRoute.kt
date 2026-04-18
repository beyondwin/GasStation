package com.gasstation.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    SettingsDetailScreen(
        section = section,
        options = uiState.optionsFor(section),
        onBackClick = onBackClick,
        onOptionClick = { option ->
            viewModel.onAction(option.action)
            onBackClick()
        },
    )
}
