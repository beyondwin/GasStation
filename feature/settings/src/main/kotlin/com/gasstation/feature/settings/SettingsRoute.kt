package com.gasstation.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(onSectionClick: (SettingsSection) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        SettingsUiState.Loading -> SettingsLoadingScreen()

        SettingsUiState.LoadFailed -> SettingsLoadFailureScreen(
            onRetry = { viewModel.onAction(SettingsAction.RetryLoad) },
        )

        is SettingsUiState.Ready -> SettingsScreen(
            uiState = state,
            onSectionClick = onSectionClick,
        )
    }
}
